package ru.otmetka.core.reports;

import java.util.List;

/**
 * Everything the PDF shows, already formatted in the plan's time zone. This
 * is the body of POST /internal/reports on notifier; see docs/architecture.md.
 * Nothing here is interpreted: the report lists what was marked and when.
 */
public record ReportData(String title, String period, String generatedAt, String timezone, List<Day> days,
        List<QuestionRow> questions) {

    public record Day(String date, List<Row> entries) {
    }

    /**
     * @param state      "marked", "no_mark" or "planned"
     * @param markedTime clock time of the mark, when there is one
     * @param corrected  the mark was changed or withdrawn at some point
     */
    public record Row(String time, String label, String state, String markedTime, boolean corrected) {
    }

    public record QuestionRow(String text, String createdOn, boolean asked) {
    }
}
