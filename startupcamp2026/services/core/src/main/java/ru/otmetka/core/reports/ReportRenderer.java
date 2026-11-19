package ru.otmetka.core.reports;

/** Turns report data into a PDF. The real implementation asks notifier over HTTP. */
public interface ReportRenderer {

    byte[] render(ReportData data);
}
