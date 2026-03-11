using System;
using System.Collections.Generic;
using System.Text;

namespace WinFormsApp2
{
    public class StepSimpleGraph : IGraph
    {
        public void Draw(PictureBox pictureBox, int[] X, int[] Y)
        {
            if (X == null || Y == null || X.Length == 0 || X.Length != Y.Length)
            {
                MessageBox.Show("Некорректные данные для построения графика.");
                return;
            }

            // Подготовка данных 
            GraphUtils.PrepareData(X, Y, 0, out double[] drawX, out double[] drawY);
            GraphUtils.SortData(ref drawX, ref drawY);

            // Создание изображения
            Bitmap bmp = new Bitmap(pictureBox.Width, pictureBox.Height);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.White);

                // Вычисление границ 
                GraphUtils.ComputeBounds(drawX, drawY, 0, 0, bmp.Width, bmp.Height,
                    out double minX, out double maxX, out double minY, out double maxY);

                // Функции преобразования координат
                Func<double, float> mapX = x => GraphUtils.MapX(x, minX, maxX, bmp.Width);
                Func<double, float> mapY = y => GraphUtils.MapY(y, minY, maxY, bmp.Height);

                using (Pen pen = new Pen(Color.Red, 2))
                {
                    List<PointF> points = new List<PointF> { new PointF(mapX(minX), mapY(0)) };

                    for (int i = 0; i < drawX.Length; i++)
                    {
                        double leftY = (i == 0) ? 0 : drawY[i - 1];
                        points.Add(new PointF(mapX(drawX[i]), mapY(leftY)));
                        points.Add(new PointF(mapX(drawX[i]), mapY(drawY[i])));

                        if (i < drawX.Length - 1)
                            points.Add(new PointF(mapX(drawX[i + 1]), mapY(drawY[i])));
                        else
                            points.Add(new PointF(mapX(maxX), mapY(drawY[i])));
                    }
                    g.DrawLines(pen, points.ToArray());
                }
            }

            pictureBox.Image?.Dispose();
            pictureBox.Image = bmp;
        }
    }
}
