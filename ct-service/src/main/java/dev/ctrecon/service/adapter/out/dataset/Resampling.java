package dev.ctrecon.service.adapter.out.dataset;

import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.matlab.MatNumericArray;

final class Resampling {

    private Resampling() {
    }

    static ImageData toSquare(MatNumericArray source, int size) {
        int sourceSize = source.rows();
        double scale = (double) sourceSize / size;
        double[] target = new double[size * size];
        for (int row = 0; row < size; row++) {
            int rowFrom = (int) Math.floor(row * scale);
            int rowTo = Math.max(rowFrom + 1, (int) Math.floor((row + 1) * scale));
            for (int column = 0; column < size; column++) {
                int columnFrom = (int) Math.floor(column * scale);
                int columnTo = Math.max(columnFrom + 1, (int) Math.floor((column + 1) * scale));
                double sum = 0.0;
                int count = 0;
                for (int sourceRow = rowFrom; sourceRow < Math.min(rowTo, sourceSize); sourceRow++) {
                    for (int sourceColumn = columnFrom; sourceColumn < Math.min(columnTo, sourceSize); sourceColumn++) {
                        sum += source.get(sourceRow, sourceColumn);
                        count++;
                    }
                }
                target[row * size + column] = sum / count;
            }
        }
        return new ImageData(size, size, target);
    }
}
