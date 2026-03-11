using System;
using System.Collections.Generic;
using System.Text;

namespace WinFormsApp2
{
    public static class GraphUtils
    {
        public static void PrepareData(int[] X, int[] Y, int type, out double[] drawX, out double[] drawY)
        {
            if (type == 0) // функция
            {
                double sum = 0;
                foreach (int val in Y) sum += val;
                if (sum == 0) sum = 1;

                drawX = new double[X.Length];
                drawY = new double[Y.Length];
                drawX[0] = X[0];
                drawY[0] = Y[0] / sum;

                for (int i = 1; i < X.Length; i++)
                {
                    drawX[i] = X[i];
                    drawY[i] = Y[i] / sum + drawY[i - 1];
                }
            }
            else // линейный
            {
                drawX = new double[X.Length];
                drawY = new double[Y.Length];
                for (int i = 0; i < X.Length; i++)
                {
                    drawX[i] = X[i];
                    drawY[i] = Y[i];
                }
            }
        }

        public static void SortData(ref double[] drawX, ref double[] drawY)
        {
            Array.Sort(drawX, drawY);
        }

        public static void ComputeBounds(double[] drawX, double[] drawY, int type, int style, int imgWidth, int imgHeight,
            out double minX, out double maxX, out double minY, out double maxY)
        {
            minX = drawX[0]; maxX = drawX[0];
            minY = drawY[0]; maxY = drawY[0];
            for (int i = 1; i < drawX.Length; i++)
            {
                if (drawX[i] < minX) minX = drawX[i];
                if (drawX[i] > maxX) maxX = drawX[i];
                if (drawY[i] < minY) minY = drawY[i];
                if (drawY[i] > maxY) maxY = drawY[i];
            }

            if (type == 0)
            {
                if (minY > 0) minY = 0;
                if (maxY < 1) maxY = 1;
            }

            if (style == 1)
            {
                if (minX > 0) minX = 0;
                if (maxX < 0) maxX = 0;
                if (minY > 0) minY = 0;
                if (maxY < 0) maxY = 0;
            }

            double padX = (maxX - minX) * 0.1;
            double padY = (maxY - minY) * 0.1;
            if (padX == 0) padX = 1;
            if (padY == 0) padY = 1;

            minX -= padX;
            maxX += padX;
            minY -= padY;
            maxY += padY;
        }

        public static float MapX(double x, double minX, double maxX, int imgWidth)
            => (float)((x - minX) / (maxX - minX) * imgWidth);

        public static float MapY(double y, double minY, double maxY, int imgHeight)
            => (float)(imgHeight - (y - minY) / (maxY - minY) * imgHeight);
    }
}
