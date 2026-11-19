from io import BytesIO

from fastapi.testclient import TestClient
from pypdf import PdfReader

from app.main import app

# What core sends: a fictional plan with generic labels.
REPORT = {
    "title": "План 1",
    "period": "8 октября 2026 — 9 октября 2026",
    "generatedAt": "9 октября 2026, 12:00",
    "timezone": "Asia/Yekaterinburg",
    "days": [
        {"date": "четверг, 8 октября", "entries": [
            {"time": "08:00", "label": "Назначение 1", "state": "marked", "markedTime": "08:05", "corrected": False},
            {"time": "20:00", "label": "Назначение 2", "state": "no_mark", "markedTime": None, "corrected": True},
        ]},
        {"date": "пятница, 9 октября", "entries": [
            {"time": "08:00", "label": "Назначение 1", "state": "marked", "markedTime": "09:30", "corrected": True},
            {"time": "20:00", "label": "Назначение 2", "state": "planned", "markedTime": None, "corrected": False},
        ]},
    ],
    "questions": [
        {"text": "Можно ли перенести время? <b>&", "createdOn": "8 октября 2026", "asked": False},
        {"text": "Второй вопрос", "createdOn": "9 октября 2026", "asked": True},
    ],
}
SECRET = {"X-Internal-Secret": "test-internal-secret"}  # set in conftest.py


def pdf_text(content: bytes) -> str:
    # Line breaks fall wherever the layout wraps; compare on a single line.
    return " ".join(" ".join(page.extract_text() for page in PdfReader(BytesIO(content)).pages).split())


def test_report_is_a_pdf_with_marks_by_day_and_questions():
    response = TestClient(app).post("/internal/reports", json=REPORT, headers=SECRET)

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/pdf"
    assert response.content.startswith(b"%PDF")
    text = pdf_text(response.content)
    assert "План 1" in text
    assert "Период: 8 октября 2026 — 9 октября 2026" in text
    # Three entries are in the past, two of them marked.
    assert "Отмечено: 2 из 3 прошедших назначений" in text
    assert "четверг, 8 октября" in text
    assert "Отмечено в 08:05" in text
    assert "Отмечено в 09:30, время исправлено" in text
    assert "Нет отметки, отметка была снята" in text
    assert "Запланировано" in text
    assert "Вопросы врачу" in text
    assert "1. Можно ли перенести время? <b>&" in text
    assert "не задан" in text
    assert "не является медицинским документом" in text


def test_empty_report():
    empty = REPORT | {"days": [], "questions": []}

    response = TestClient(app).post("/internal/reports", json=empty, headers=SECRET)

    text = pdf_text(response.content)
    assert "За этот период назначений нет." in text
    assert "Вопросов нет." in text


def test_secret_is_required():
    client = TestClient(app)

    assert client.post("/internal/reports", json=REPORT).status_code == 401
    assert client.post("/internal/reports", json=REPORT, headers={"X-Internal-Secret": "wrong"}).status_code == 401
