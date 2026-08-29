package com.teosa.app.prototipo;

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
        if (description == null || description.trim().isEmpty()) {
            return 0;
        }

        int charactersPerLine = Math.max(12, (int) Math.floor(availableWidth / 7.0));
        String[] explicitLines = description.trim().split("\\R", -1);
        int visualLines = 0;
        for (String line : explicitLines) {
            visualLines += Math.max(1,
                    (int) Math.ceil(line.length() / (double) charactersPerLine));
        }
        return Math.max(PHOTO_CAPTION_HEIGHT, visualLines * 16.0 + 6.0);
    }

    static double estimateCategoryTitleHeight(String title) {
        if (title == null || title.trim().isEmpty()) {
            return CATEGORY_TITLE_HEIGHT;
        }
        int visualLines = Math.max(1, (int) Math.ceil(title.trim().length() / 55.0));
        return Math.max(CATEGORY_TITLE_HEIGHT, visualLines * 18.0 + 10.0);
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
        return Math.max(0, CONTENT_HEIGHT
                - DOCUMENT_HEADER_HEIGHT
                - GENERAL_DATA_HEIGHT
                - REPORT_TOP_SPACING
                - (SECTION_HEADER_HEIGHT * 2.0)
                - estimateBodyHeight(equipmentData)
                - estimateBodyHeight(workDescription));
    }
}
