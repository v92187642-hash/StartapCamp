"""The PDF summary for a doctor's visit.

core sends the data, already formatted; this module only lays it out. Nothing
is stored and nothing is interpreted: the report lists what was marked.
"""

import hmac
from io import BytesIO
from pathlib import Path

from fastapi import APIRouter, Header, HTTPException, Response
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from app.config import settings
from app.events import Event

FONTS = Path(__file__).parent / "fonts"
# The built-in PDF fonts have no Cyrillic letters.
pdfmetrics.registerFont(TTFont("DejaVu", FONTS / "DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", FONTS / "DejaVuSans-Bold.ttf"))

TITLE = ParagraphStyle("title", fontName="DejaVu-Bold", fontSize=18, leading=24, spaceAfter=4)
HEADING = ParagraphStyle("heading", fontName="DejaVu-Bold", fontSize=13, leading=18, spaceBefore=14, spaceAfter=6)
DAY = ParagraphStyle("day", fontName="DejaVu-Bold", fontSize=11, leading=15, spaceBefore=10, spaceAfter=3)
TEXT = ParagraphStyle("text", fontName="DejaVu", fontSize=10.5, leading=15)
SMALL = ParagraphStyle("small", fontName="DejaVu", fontSize=8.5, leading=12, textColor=colors.grey)


class Row(Event):
    time: str
    label: str
    state: str  # marked | no_mark | planned
    marked_time: str | None = None
    corrected: bool = False


class Day(Event):
    date: str
    entries: list[Row]


class QuestionRow(Event):
    text: str
    created_on: str
    asked: bool


class ReportData(Event):
    title: str
    period: str
    generated_at: str
    timezone: str
    days: list[Day]
    questions: list[QuestionRow]


def state_text(row: Row) -> str:
    if row.state == "marked":
        return f"Отмечено в {row.marked_time}" + (", время исправлено" if row.corrected else "")
    if row.state == "no_mark":
        return "Нет отметки" + (", отметка была снята" if row.corrected else "")
    return "Запланировано"


def escape(text: str) -> str:
    """Paragraph reads a small HTML dialect; user text must not be taken for markup."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def render_report(data: ReportData) -> bytes:
    buffer = BytesIO()
    document = SimpleDocTemplate(
        buffer, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm, bottomMargin=16 * mm,
        title="Отметка: отчёт", author="Отметка",
    )
    rows = [row for day in data.days for row in day.entries]
    past = [row for row in rows if row.state != "planned"]
    marked = [row for row in past if row.state == "marked"]

    story = [
        Paragraph(escape(data.title), TITLE),
        Paragraph("Дневник выполнения назначений врача", TEXT),
        Paragraph(f"Период: {escape(data.period)}", TEXT),
        Paragraph(f"Отмечено: {len(marked)} из {len(past)} прошедших назначений", TEXT),
        Paragraph("Отметки по дням", HEADING),
    ]
    if not data.days:
        story.append(Paragraph("За этот период назначений нет.", TEXT))
    for day in data.days:
        story.append(Paragraph(escape(day.date), DAY))
        table = Table(
            [[row.time, Paragraph(escape(row.label), TEXT), Paragraph(state_text(row), TEXT)] for row in day.entries],
            colWidths=[18 * mm, 62 * mm, 94 * mm],
        )
        table.setStyle(TableStyle([
            ("FONTNAME", (0, 0), (-1, -1), "DejaVu"),
            ("FONTSIZE", (0, 0), (-1, -1), 10.5),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LINEBELOW", (0, 0), (-1, -1), 0.25, colors.lightgrey),
            ("TOPPADDING", (0, 0), (-1, -1), 3),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ]))
        story.append(table)

    story.append(Paragraph("Вопросы врачу", HEADING))
    if not data.questions:
        story.append(Paragraph("Вопросов нет.", TEXT))
    for number, question in enumerate(data.questions, start=1):
        status = "задан" if question.asked else "не задан"
        story.append(Paragraph(f"{number}. {escape(question.text)}", TEXT))
        story.append(Paragraph(f"Записан {escape(question.created_on)}, {status}", SMALL))
        story.append(Spacer(1, 4))

    story.append(Spacer(1, 14))
    story.append(Paragraph(
        f"Сформировано {escape(data.generated_at)} ({escape(data.timezone)}). Отчёт составлен по отметкам "
        "пользователя, не является медицинским документом и не содержит медицинских рекомендаций.",
        SMALL,
    ))
    document.build(story)
    return buffer.getvalue()


router = APIRouter()


@router.post("/internal/reports", response_class=Response)
def build_report(data: ReportData, x_internal_secret: str = Header(default="")) -> Response:
    """Called by core only. Clients never reach this service."""
    if not hmac.compare_digest(x_internal_secret.encode(), settings.internal_secret.encode()):
        raise HTTPException(status_code=401, detail="Wrong internal secret")
    return Response(content=render_report(data), media_type="application/pdf")
