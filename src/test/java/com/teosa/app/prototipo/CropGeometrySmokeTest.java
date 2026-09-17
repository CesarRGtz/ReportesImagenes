package com.teosa.app.prototipo;

import com.teosa.app.prototipo.presentation.ImageEditorDialog;

import javafx.scene.shape.Rectangle;

public class CropGeometrySmokeTest {
    public static void main(String[] args) {
        for (double[] image : new double[][] {{860, 520}, {300, 520}, {520, 520}}) {
            for (double ratio : new double[] {1, 4.0 / 3, 1.5, 16.0 / 9, 0.75, 2.0 / 3, 9.0 / 16}) {
                Rectangle crop = new Rectangle(image[0] - 20, image[1] - 20, 20, 20);
                for (double size : new double[] {0.05, 0.8, 1, 0.3}) {
                    ImageEditorDialog.resizeCrop(crop, ratio, size, image[0], image[1]);
                    if (Math.abs(crop.getWidth() / crop.getHeight() - ratio) > 1e-9
                            || crop.getX() < 0 || crop.getY() < 0
                            || crop.getX() + crop.getWidth() > image[0] + 1e-9
                            || crop.getY() + crop.getHeight() > image[1] + 1e-9) {
                        throw new AssertionError("El recorte debe conservar su proporción y quedar dentro de la imagen");
                    }
                }
            }
        }
        Rectangle centered = new Rectangle(100, 100, 200, 200);
        ImageEditorDialog.resizeCrop(centered, 1, 0.2, 500, 500);
        if (centered.getX() != 150 || centered.getY() != 150) {
            throw new AssertionError("Cambiar el tamaño debe conservar el centro cuando hay espacio");
        }
        System.out.println("CROP_GEOMETRY_OK");
    }
}
