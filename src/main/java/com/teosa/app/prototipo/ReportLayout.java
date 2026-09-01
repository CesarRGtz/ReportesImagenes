package com.teosa.app.prototipo;

import com.teosa.app.prototipo.data.HeaderLine;
import com.teosa.app.prototipo.data.TemplateDefinition;

final class ReportLayout {

    static final double PAGE_WIDTH = 612.0;
    static final double PAGE_HEIGHT = 792.0;
    static final double MARGIN_HORIZONTAL = 50.0;
    static final double MARGIN_VERTICAL = 45.0;
    static final double CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2.0);
    static final double CONTENT_HEIGHT = PAGE_HEIGHT - (MARGIN_VERTICAL * 2.0);
    static final double DOCUMENT_HEADER_HEIGHT = 112.0;
    static final double GENERAL_DATA_HEIGHT = 135.0;
    static final double REPORT_TOP_SPACING = 10.0;
    static final double SECTION_HEADER_HEIGHT = 28.0;
    static final double PHOTO_SECTION_HEIGHT = 45.0;
    static final double CATEGORY_TITLE_HEIGHT = 34.0;
    static final double PHOTO_CELL_PADDING = 8.0;
    static final double PHOTO_GAP = 14.2; // Aproximadamente 0.5 cm.
    static final double MAX_PHOTO_WIDTH = CONTENT_WIDTH - (PHOTO_CELL_PADDING * 2.0);
    static final double PHOTO_CAPTION_HEIGHT = 22.0;
    static final double PHOTO_SPACING = 8.0;
    static final double MIN_PHOTO_WIDTH = 100.0;
    static final double DEFAULT_PHOTO_WIDTH = MAX_PHOTO_WIDTH;

    private ReportLayout() {
    }

    static double[] scaleImage(double originalWidth, double originalHeight,
                               double requestedWidth, double maxHeight) {
        return scaleImage(originalWidth, originalHeight, requestedWidth,
                MAX_PHOTO_WIDTH, maxHeight);
    }

    static double[] scaleImage(double originalWidth, double originalHeight,
                               double requestedWidth, double maxWidth, double maxHeight) {
        if (originalWidth <= 0 || originalHeight <= 0 || maxHeight <= 0) {
            return new double[]{0, 0};
        }

        double allowedWidth = Math.min(Math.max(1, maxWidth), MAX_PHOTO_WIDTH);
        double width = Math.min(Math.max(requestedWidth, MIN_PHOTO_WIDTH), allowedWidth);
        double height = originalHeight * (width / originalWidth);

        if (height > maxHeight) {
            double scale = maxHeight / height;
            width *= scale;
            height = maxHeight;
        }

        return new double[]{width, height};
    }

    static double estimateDescriptionHeight(String description) {
        return estimateDescriptionHeight(description, MAX_PHOTO_WIDTH);
    }

    static double estimateDescriptionHeight(String description, double availableWidth) {
        return estimateDescriptionHeight(description, availableWidth, 11.0);
    }

    static double estimateDescriptionHeight(String description, double availableWidth,
                                            double fontSize) {
        if (description == null || description.trim().isEmpty()) {
            return 0;
        }

        double characterWidth = Math.max(4.0, fontSize * 0.58);
        int charactersPerLine = Math.max(8, (int) Math.floor(availableWidth / characterWidth));
        String[] explicitLines = description.trim().split("\\R", -1);
        int visualLines = 0;
        for (String line : explicitLines) {
            visualLines += Math.max(1,
                    (int) Math.ceil(line.length() / (double) charactersPerLine));
        }
        return Math.max(fontSize * 1.5 + 6.0, visualLines * fontSize * 1.3 + 6.0);
    }

    static double estimateCategoryTitleHeight(String title) {
        return estimateCategoryTitleHeight(title, 14.0);
    }

    static double estimateCategoryTitleHeight(String title, double fontSize) {
        if (title == null || title.trim().isEmpty()) {
            return CATEGORY_TITLE_HEIGHT;
        }
        int characters = Math.max(20, (int) Math.floor(
                CONTENT_WIDTH / Math.max(5.0, fontSize * 0.58)));
        int visualLines = Math.max(1, (int) Math.ceil(title.trim().length() / (double) characters));
        return Math.max(CATEGORY_TITLE_HEIGHT, visualLines * fontSize * 1.3 + 16.0);
    }

    static double photoCellWidth(double requestedWidth) {
        return Math.min(Math.max(requestedWidth, MIN_PHOTO_WIDTH), MAX_PHOTO_WIDTH);
    }

    static double estimateBodyHeight(String text) {
        String value = text == null || text.trim().isEmpty() ? "—" : text.trim();
        String[] explicitLines = value.split("\\R", -1);
        int visualLines = 0;
        for (String line : explicitLines) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() / 72.0));
        }
        return Math.max(30.0, visualLines * 14.0 + 16.0);
    }

    static double initialPhotoSpace(String equipmentData, String workDescription) {
        return initialPhotoSpace(equipmentData, workDescription,
                DOCUMENT_HEADER_HEIGHT, 5);
    }

    static double initialPhotoSpace(String equipmentData, String workDescription,
                                    double documentHeaderHeight, int visibleFieldCount) {
        return Math.max(0, CONTENT_HEIGHT
                - documentHeaderHeight
                - generalDataHeight(visibleFieldCount)
                - REPORT_TOP_SPACING
                - (SECTION_HEADER_HEIGHT * 2.0)
                - estimateBodyHeight(equipmentData)
                - estimateBodyHeight(workDescription));
    }

    static double initialPhotoSpace(String equipmentData, String workDescription,
                                    TemplateDefinition template, int visibleFieldCount) {
        return Math.max(0, CONTENT_HEIGHT
                - estimateDocumentHeaderHeight(template)
                - generalDataHeight(visibleFieldCount)
                - REPORT_TOP_SPACING
                - estimateSectionHeaderHeight(template.getSection1Title())
                - estimateBodyHeight(equipmentData)
                - estimateSectionHeaderHeight(template.getSection2Title())
                - estimateBodyHeight(workDescription));
    }

    static double generalDataHeight(int visibleFieldCount) {
        return Math.max(0, visibleFieldCount) * 27.0;
    }

    static double estimateDocumentHeaderHeight(TemplateDefinition template) {
        if (template == null) return DOCUMENT_HEADER_HEIGHT;
        double textWidth = "STACKED".equals(template.getHeaderLayout())
                ? CONTENT_WIDTH
                : estimateHeaderTextWidth(template, "NOMBRE DE LA EMPRESA");
        double textHeight = 0;
        for (HeaderLine line : template.getHeaderLines()) {
            double size = line.getStyle().getFontSize();
            int characters = Math.max(8, (int) Math.floor(
                    textWidth / Math.max(4.0, size * 0.58)));
            String text = line.getText().replace("{empresa}", "NOMBRE DE LA EMPRESA");
            int visualLines = Math.max(1, (int) Math.ceil(text.length() / (double) characters));
            textHeight += Math.max(12.0, visualLines * size * 1.25);
        }
        double imageHeight = template.getHeaderImageWidth()
                / template.getHeaderImageAspectRatio();
        double contentHeight = "STACKED".equals(template.getHeaderLayout())
                ? imageHeight + template.getHeaderGap() + textHeight
                : Math.max(imageHeight, textHeight);
        return contentHeight + 22.0;
    }

    static double estimateHeaderTextWidth(TemplateDefinition template, String company) {
        double maximumAvailable = Math.max(120, CONTENT_WIDTH
                - template.getHeaderImageWidth() - template.getHeaderGap() - 10);
        if ("JUSTIFY".equals(template.getHeaderTextAlignment())) {
            return Math.min(330, maximumAvailable);
        }
        double estimated = 120;
        for (HeaderLine line : template.getHeaderLines()) {
            String text = line.getText().replace("{empresa}",
                    company == null || company.isBlank() ? "NOMBRE DE LA EMPRESA" : company);
            estimated = Math.max(estimated,
                    text.length() * line.getStyle().getFontSize() * 0.58 + 8);
        }
        return Math.min(Math.min(330, maximumAvailable), estimated);
    }

    static double estimateSectionHeaderHeight(String title) {
        String value = title == null || title.isBlank() ? "—" : title.trim();
        int lines = Math.max(1, (int) Math.ceil(value.length() / 68.0));
        return Math.max(SECTION_HEADER_HEIGHT, lines * 14.0 + 12.0);
    }

    static double estimatePhotoSectionHeight(String title, boolean continuation) {
        String value = title == null ? "" : title;
        if (continuation) value += " (CONTINUACIÓN)";
        return Math.max(PHOTO_SECTION_HEIGHT, estimateSectionHeaderHeight(value));
    }
}
