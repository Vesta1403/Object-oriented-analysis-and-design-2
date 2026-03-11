using System;
using System.Collections.Generic;
using System.Text;

namespace WinFormsApp2
{
    using System;
    using System.Collections.Generic;
    using System.Drawing;
    using System.Drawing.Drawing2D;
    using System.Windows.Forms;

    public class LineStyledGraph : IGraph
    {
        public void Draw(PictureBox pictureBox, int[] X, int[] Y)
        {
            if (X == null || Y == null || X.Length == 0 || X.Length != Y.Length)
            {
                MessageBox.Show("Некорректные данные для построения графика.");
                return;
            }

            // Подготовка данных  
            GraphUtils.PrepareData(X, Y, 1, out double[] drawX, out double[] drawY);
            GraphUtils.SortData(ref drawX, ref drawY);

            // Создание изображения
            Bitmap bmp = new Bitmap(pictureBox.Width, pictureBox.Height);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.White);

                // Вычисление границ 
                GraphUtils.ComputeBounds(drawX, drawY, 1, 1, bmp.Width, bmp.Height,
                    out double minX, out double maxX, out double minY, out double maxY);

                // Функции преобразования координат
                Func<double, float> mapX = x => GraphUtils.MapX(x, minX, maxX, bmp.Width);
                Func<double, float> mapY = y => GraphUtils.MapY(y, minY, maxY, bmp.Height);

                float yZero = mapY(0);
                float xZero = mapX(0);

                using (Pen pen = new Pen(Color.Blue, 2))
                using (Brush brush = new SolidBrush(Color.Blue))
                using (Pen axisPen = new Pen(Color.Black, 1))
                using (Pen dashPen = new Pen(Color.Gray, 1) { DashStyle = DashStyle.Dash })
                {
                    // Оси через ноль
                    g.DrawLine(axisPen, 0, yZero, bmp.Width, yZero);  // ось X (Y=0)
                    g.DrawLine(axisPen, xZero, 0, xZero, bmp.Height); // ось Y (X=0)

                    //  Пунктир
                    for (int i = 0; i < drawX.Length; i++)
                    {
                        float x = mapX(drawX[i]);
                        float y = mapY(drawY[i]);
                        g.DrawLine(dashPen, x, y, x, yZero); // вертикальная к оси X
                        g.DrawLine(dashPen, x, y, xZero, y); // горизонтальная к оси Y
                    }

                    // Соединение точек
                    PointF[] points = new PointF[drawX.Length];
                    for (int i = 0; i < drawX.Length; i++)
                        points[i] = new PointF(mapX(drawX[i]), mapY(drawY[i]));
                    g.DrawLines(pen, points);

                    //  Жирные точки 
                    float bigPointSize = 7;
                    for (int i = 0; i < drawX.Length; i++)
                    {
                        float x = mapX(drawX[i]);
                        float y = mapY(drawY[i]);
                        g.FillEllipse(brush, x - bigPointSize / 2, y - bigPointSize / 2, bigPointSize, bigPointSize);
                    }

                    // Значения 
                    using (Font font = new Font("Arial", 8))
                    using (StringFormat sfCenter = new StringFormat() { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Near })
                    using (StringFormat sfRight = new StringFormat() { Alignment = StringAlignment.Far, LineAlignment = StringAlignment.Center })
                    {
                        // Ось X
                        HashSet<double> uniqueX = new HashSet<double>();
                        foreach (double xVal in drawX)
                        {
                            if (uniqueX.Add(xVal))
                            {
                                float xPos = mapX(xVal);
                                g.DrawLine(axisPen, xPos, yZero - 3, xPos, yZero + 3); // метка
                                g.DrawString(xVal.ToString("0.##"), font, Brushes.Black, xPos, yZero + 5, sfCenter);
                            }
                        }

                        // Ось Y
                        HashSet<double> uniqueY = new HashSet<double>();
                        foreach (double yVal in drawY)
                        {
                            if (uniqueY.Add(yVal))
                            {
                                float yPos = mapY(yVal);
                                g.DrawLine(axisPen, xZero - 3, yPos, xZero + 3, yPos); // метка
                                g.DrawString(yVal.ToString("0.##"), font, Brushes.Black, xZero - 5, yPos - 6, sfRight);
                            }
                        }
                    }
                }
            }

            // Вывод изображения
            pictureBox.Image?.Dispose();
            pictureBox.Image = bmp;
        }
    }
}
