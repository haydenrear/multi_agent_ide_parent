package com.hayden.utilitymodule;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.Shape;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@UtilityClass
public class NdUtilities {

    public static float[][] get2dArray(NDArray array) {
        long[] shape = array.getShape().getShape();
        float[][] arr = new float[Math.toIntExact(shape[0])]
                [Math.toIntExact(shape[1])];
        for (int i=0; i<shape[0]; ++i) {
            arr[i] = array.get(i).toFloatArray();
        }
        return arr;
    }

    public static float[][][] get3dArray(NDArray array) {
        long[] shape = array.getShape().getShape();
        float[][][] arr = new float[Math.toIntExact(shape[0])]
                [Math.toIntExact(shape[1])]
                [Math.toIntExact(shape[2])];
        for (int i=0; i<shape[0]; ++i) {
            for (int j=0; j<shape[1]; ++j) {
                arr[i][j] = array.get(i, j).toFloatArray();
            }
        }
        return arr;
    }


    public static NDArray create2dArray(float[][] array, NDManager ndManager) {
        NDArray ndArray = ndManager.create(new Shape(
                array.length,
                arrLength(array)
        ));
        for (int i=0; i<array.length; ++i) {
            ndArray.set(new NDIndex(i), ndManager.create(array[i]));
        }
        return ndArray;
    }

    public static NDArray create2dArray(double[][] array, NDManager ndManager) {
        NDArray ndArray = ndManager.create(new Shape(
                array.length,
                arrLength(array)
        ));
        for (int i=0; i<array.length; ++i) {
            ndArray.set(new NDIndex(i), ndManager.create(array[i]));
        }
        return ndArray;
    }

    public static NDArray create3dArray(float[][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] shapeArr = shape.getShape();
        for (int i=0; i<shapeArr[0]; ++i) {
            for (int j = 0; j < shapeArr[1]; ++j) {
                ndArray.set(new NDIndex(i, j), ndManager.create(array[i][j]));
            }
        }
        return ndArray;
    }

    public static NDArray create3dArray(double[][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] shapeArr = shape.getShape();
        for (int i=0; i<shapeArr[0]; ++i) {
            for (int j = 0; j < shapeArr[1]; ++j) {
                ndArray.set(new NDIndex(i, j), ndManager.create(array[i][j]));
            }
        }
        return ndArray;
    }

    public static NDArray create3dArray(Double[][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] shapeArr = shape.getShape();
        for (int i=0; i<shapeArr[0]; ++i) {
            for (int j = 0; j < shapeArr[1]; ++j) {
                ndArray.set(new NDIndex(i, j), ndManager.create(ArrayUtils.toPrimitive(array[i][j])));
            }
        }
        return ndArray;
    }

    public static NDArray create4DArray(float[][][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1),
                arrLength(array, 2)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] shapeArr = shape.getShape();
        for (int i = 0; i< shapeArr[0]; ++i) {
            for (int j=0; j<shapeArr[1]; ++j) {
                for (int q = 0; q < shapeArr[2]; ++q) {
                    ndArray.set(new NDIndex(i, j, q), ndManager.create(array[i][j][q]));
                }
            }
        }
        return ndArray;
    }

    public static NDArray create4DArray(double[][][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1),
                arrLength(array, 2)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] shapeArr = shape.getShape();
        for (int i=0; i<shapeArr[0]; ++i) {
            for (int j=0; j<shapeArr[1]; ++j) {
                for (int q = 0; q < shapeArr[2]; ++q) {
                    ndArray.set(new NDIndex(i, j, q), ndManager.create(array[i][j][q]));
                }
            }
        }
        return ndArray;
    }

    public static NDArray create4DArray(Double[][][][] array, NDManager ndManager) {
        Shape shape = new Shape(
                array.length,
                arrLength(array, 0),
                arrLength(array, 1),
                arrLength(array, 2)
        );
        NDArray ndArray = ndManager.create(shape);
        long[] arrShape = shape.getShape();
        for (int i=0; i<arrShape[0]; ++i) {
            for (int j=0; j<arrShape[1]; ++j) {
                for (int q = 0; q < arrShape[2]; ++q) {
                    ndArray.set(new NDIndex(i, j, q), ndManager.create(ArrayUtils.toPrimitive(array[i][j][q])));
                }
            }
        }
        return ndArray;
    }

    public static double[][][] get3dArrayDouble(NDArray array) {
        float[][][] dArray = get3dArray(array);
        double[][][] doubleArray = new double
                [dArray.length]
                [arrLength(dArray, 0)]
                [arrLength(dArray, 1)];
        for (int i=0; i<doubleArray.length; ++i) {
            for (int j=0; j<doubleArray[0].length; ++j) {
                for (int n=0; n<doubleArray[0][0].length; ++n) {
                    doubleArray[i][j][n] = dArray[i][j][n];
                }
            }
        }
        return doubleArray;
    }

    public static float[][][][] get4dArray(NDArray array) {
        long[] shape = array.getShape().getShape();
        float[][][][] arr = new float[Math.toIntExact(shape[0])]
                [Math.toIntExact(shape[1])]
                [Math.toIntExact(shape[2])]
                [Math.toIntExact(shape[3])];
        for (int i=0; i<shape[0]; ++i) {
            for (int j=0; j<shape[1]; ++j) {
                for (int t=0; t<shape[2]; ++t) {
                    arr[i][j][t] = array.get(i, j, t).toFloatArray();
                }
            }
        }
        return arr;
    }

    public static double[][][][] get4dArrayDouble(NDArray array) {
        float[][][][] dArray = get4dArray(array);
        long[] shape = array.getShape().getShape();
        double[][][][] doubleArray = new double[Math.toIntExact(shape[0])]
                [Math.toIntExact(shape[1])]
                [Math.toIntExact(shape[2])]
                [Math.toIntExact(shape[3])];


        for (int i=0; i<dArray.length; ++i) {
            for (int j=0; j<doubleArray[0].length; ++j) {
                for (int n=0; n<doubleArray[0][0].length; ++n) {
                    for (int q=0; q<doubleArray[0][0][0].length; ++q) {
                        doubleArray[i][j][n][q] = dArray[i][j][n][q];
                    }
                }
            }
        }
        return doubleArray;
    }

    public static double[][] get2dArrayDouble(NDArray ndArray) {
        float[][] dArray = get2dArray(ndArray);
        long[] shape = ndArray.getShape().getShape();
        double[][] doubleArray = new double[Math.toIntExact(shape[0])]
                [Math.toIntExact(shape[1])];
        for (int i=0; i<dArray.length; ++i) {
            for (int j=0; j<dArray[0].length; ++j) {
                doubleArray[i][j] = dArray[i][j];
            }
        }
        return doubleArray;
    }

    @NotNull
    private static Integer arrLength(float[][] dArray) {
        return Arrays.stream(dArray)
                .map(f -> f.length)
                .max(Integer::compareTo)
                .orElse(dArray[0].length);
    }

    @NotNull
    private static Integer arrLength(double[][] dArray) {
        return Arrays.stream(dArray)
                .map(f -> f.length)
                .max(Integer::compareTo)
                .orElse(dArray[0].length) ;
    }

    @NotNull
    private static Integer arrLength(float[][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        }  else {
            return 0;
        }
    }

    @NotNull
    private static Integer arrLength(double[][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        }  else {
            return 0;
        }
    }

    @NotNull
    private static Integer arrLength(float[][][][] dArray) {
        return Arrays.stream(dArray)
                .map(f -> f.length)
                .max(Integer::compareTo)
                .orElse(dArray[0].length);
    }

    @NotNull
    private static Integer arrLength(float[][][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        } else if (dim == 2) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0][0].length);
        } else {
            return 0;
        }
    }

    @NotNull
    private static Integer arrLength(double[][][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        } else if (dim == 2) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0][0].length);
        } else {
            return 0;
        }
    }

    @NotNull
    private static <N extends Number>  Integer arrLength(N[][] dArray) {
        return Arrays.stream(dArray)
                .map(f -> f.length)
                .max(Integer::compareTo)
                .orElse(dArray[0].length);
    }

    @NotNull
    private static <N extends Number>  Integer arrLength(N[][][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        } else if (dim == 2) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0][0].length);
        } else {
            return 0;
        }
    }

    @NotNull
    private static <N extends Number>  Integer arrLength(N[][][] dArray, int dim) {
        if (dim == 0) {
            return Arrays.stream(dArray)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0].length);
        } else if (dim == 1) {
            return Arrays.stream(dArray)
                    .flatMap(Arrays::stream)
                    .map(f -> f.length)
                    .max(Integer::compareTo)
                    .orElse(dArray[0][0].length);
        } else {
            return 0;
        }
    }


}
