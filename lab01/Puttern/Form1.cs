namespace WinFormsApp2
{
    public partial class Form1 : Form
    {
        public Form1()
        {
            InitializeComponent();
        }

        private void radioButton1_CheckedChanged(object sender, EventArgs e)
        {

        }

        private void pictureBox1_Click(object sender, EventArgs e)
        {

        }

        private void dataGridView1_CellContentClick(object sender, DataGridViewCellEventArgs e)
        {

        }

        private void button1_Click(object sender, EventArgs e) // Go
        {
            // Создаем временные списки
            List<int> xList = new List<int>();
            List<int> yList = new List<int>();

            foreach (DataGridViewRow row in dataGridView1.Rows)
            {
                if (row.IsNewRow) continue;

                object xValue = row.Cells["ColumnX"].Value;
                object yValue = row.Cells["ColumnY"].Value;

                if (xValue != null && yValue != null &&
                    int.TryParse(xValue.ToString(), out int x) &&
                    int.TryParse(yValue.ToString(), out int y))
                {
                    xList.Add(x);
                    yList.Add(y);
                }
            }

            int[] X = xList.ToArray();
            int[] Y = yList.ToArray();

            // Определяем Type (0 - функция, 1 - полигон)
            int type = radioButton1.Checked ? 0 : 1;   

            // Определяем Style (0 - простой, 1 - стилизованный)
            int style = radioButton3.Checked ? 0 : 1; 

            // Выбираем фабрику по типу
            IGraphFactory factory = type == 0 ? new StepGraphFactory() : new LineGraphFactory();

            // Создаём график по стилю
            IGraph graph = style == 0 ? factory.CreateSimpleGraph() : factory.CreateStyledGraph();

            // Рисуем
            graph.Draw(pictureBox1, X, Y);
        }

        private void radioButton2_CheckedChanged(object sender, EventArgs e)
        {

        }

        private void radioButton2_CheckedChanged_1(object sender, EventArgs e)
        {

        }
    }
}
