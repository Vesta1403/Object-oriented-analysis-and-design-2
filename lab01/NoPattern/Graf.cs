using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Linq;
using System.Windows.Forms;

namespace WinFormsApp2
{
    public class Graf
    {
        public int[] X { get; set; }
        public int[] Y { get; set; }
        public int Type { get; set; }
        public int Style { get; set; }

        public void Draw(PictureBox pictureBox)
        {
            if (X == null || Y == null || X.Length == 0 || Y.Length == 0 || X.Length != Y.Length)
            {
                MessageBox.Show("Некорректные данные для построения графика.");
                return;
            }

            // Подготовка данных для отрисовки
            double[] drawX = new double[X.Length];
            double[] drawY = new double[Y.Length];

            if (Type == 0) // ступенчатый график с накоплением Y
            {
                double sum = 0;
                foreach (int val in Y) sum += val;
                if (sum == 0) sum = 1;

                drawX[0] = X[0];
                drawY[0] = Y[0] / sum;

                for (int i = 1; i < X.Length; i++)
                {
                    drawX[i] = X[i];
                    drawY[i] = Y[i] / sum + drawY[i - 1];
                }
            }
            else // Type == 1: линейный график (исходные координаты)
            {
                for (int i = 0; i < X.Length; i++)
                {
                    drawX[i] = X[i];
                    drawY[i] = Y[i];
                }
            }

            // Сортировка точек по возрастанию X для корректного соединения линиями
            var indices = Enumerable.Range(0, drawX.Length).ToArray();
            Array.Sort(indices, (a, b) => drawX[a].CompareTo(drawX[b]));
            double[] sortedX = new double[drawX.Length];
            double[] sortedY = new double[drawY.Length];
            for (int i = 0; i < indices.Length; i++)
            {
                sortedX[i] = drawX[indices[i]];
                sortedY[i] = drawY[indices[i]];
            }
            drawX = sortedX;
            drawY = sortedY;

            // Создание изображения
            Bitmap bmp = new Bitmap(pictureBox.Width, pictureBox.Height);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.White);

                // Вычисление границ данных
                double minX = drawX[0], maxX = drawX[0];
                double minY = drawY[0], maxY = drawY[0];
                for (int i = 1; i < drawX.Length; i++)
                {
                    if (drawX[i] < minX) minX = drawX[i];
                    if (drawX[i] > maxX) maxX = drawX[i];
                    if (drawY[i] < minY) minY = drawY[i];
                    if (drawY[i] > maxY) maxY = drawY[i];
                }

                // Для Type == 0 расширяем диапазон Y до [0,1]
                if (Type == 0)
                {
                    if (minY > 0) minY = 0;
                    if (maxY < 1) maxY = 1;
                }

                // Для Style == 1 гарантируем, что начало координат (0,0) входит в диапазон
                if (Style == 1)
                {
                    if (minX > 0) minX = 0;
                    if (maxX < 0) maxX = 0;
                    if (minY > 0) minY = 0;
                    if (maxY < 0) maxY = 0;
                }

                // Отступы по краям
                double padX = (maxX - minX) * 0.1;
                double padY = (maxY - minY) * 0.1;
                if (padX == 0) padX = 1;
                if (padY == 0) padY = 1;

                minX -= padX;
                maxX += padX;
                minY -= padY;
                maxY += padY;

                int imgWidth = bmp.Width;
                int imgHeight = bmp.Height;

                // Функции преобразования координат
                Func<double, float> mapX = (x) => (float)((x - minX) / (maxX - minX) * imgWidth);
                Func<double, float> mapY = (y) => (float)(imgHeight - (y - minY) / (maxY - minY) * imgHeight);

                // Цвет в зависимости от Style
                Color color = (Style == 0) ? Color.Red : Color.Blue;

                using (Pen pen = new Pen(color, 2))
                using (Brush brush = new SolidBrush(color))
                using (Pen axisPen = new Pen(Color.Black, 1))
                using (Pen dashPen = new Pen(Color.Gray, 1) { DashStyle = DashStyle.Dash })
                {
                    float yZero = mapY(0);
                    float xZero = mapX(0);

                    // Дополнительные элементы только для Style == 1
                    if (Style == 1)
                    {
                        // Оси через 0
                        g.DrawLine(axisPen, 0, yZero, imgWidth, yZero); // ось X (Y=0)
                        g.DrawLine(axisPen, xZero, 0, xZero, imgHeight); // ось Y (X=0)

                        // Пунктирные линии от точек к осям
                        for (int i = 0; i < drawX.Length; i++)
                        {
                            float x = mapX(drawX[i]);
                            float y = mapY(drawY[i]);
                            g.DrawLine(dashPen, x, y, x, yZero); // вертикальная к оси X
                            g.DrawLine(dashPen, x, y, xZero, y); // горизонтальная к оси Y
                        }
                    }

                    // Рисование графика
                    if (Type == 0) // ступенчатый
                    {
                        List<PointF> points = new List<PointF>();
                        points.Add(new PointF(mapX(minX), mapY(0))); // начало от левой границы

                        for (int i = 0; i < drawX.Length; i++)
                        {
                            double leftY = (i == 0) ? 0 : drawY[i - 1];
                            points.Add(new PointF(mapX(drawX[i]), mapY(leftY))); // вертикаль в узле
                            points.Add(new PointF(mapX(drawX[i]), mapY(drawY[i]))); // уровень узла

                            if (i < drawX.Length - 1)
                                points.Add(new PointF(mapX(drawX[i + 1]), mapY(drawY[i]))); // горизонталь к след. узлу
                            else
                                points.Add(new PointF(mapX(maxX), mapY(drawY[i]))); // горизонталь к правой границе
                        }
                        g.DrawLines(pen, points.ToArray());
                    }
                    else // Type == 1: линейный график (соединяем точки линиями)
                    {
                        PointF[] points = new PointF[drawX.Length];
                        for (int i = 0; i < drawX.Length; i++)
                            points[i] = new PointF(mapX(drawX[i]), mapY(drawY[i]));
                        g.DrawLines(pen, points);
                    }

                    // Жирные точки и подписи на осях для Style == 1
                    if (Style == 1)
                    {
                        // Жирные точки
                        float bigPointSize = 7;
                        for (int i = 0; i < drawX.Length; i++)
                        {
                            float x = mapX(drawX[i]);
                            float y = mapY(drawY[i]);
                            g.FillEllipse(brush, x - bigPointSize / 2, y - bigPointSize / 2, bigPointSize, bigPointSize);
                        }

                        // Подписи значений на осях
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
            }

            // Вывод изображения
            pictureBox.Image?.Dispose();
            pictureBox.Image = bmp;
        }
    }
}