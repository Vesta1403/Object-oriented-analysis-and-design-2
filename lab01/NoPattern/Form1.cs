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

            // Проходим по всем строкам DataGridView (кроме последней пустой, если AllowUserToAddRows = true)
            foreach (DataGridViewRow row in dataGridView1.Rows)
            {
                // Пропускаем новую строку (если она есть)
                if (row.IsNewRow) continue;

                // Получаем значения ячеек
                object xValue = row.Cells["ColumnX"].Value;
                object yValue = row.Cells["ColumnY"].Value;

                // Проверяем, что обе ячейки не пусты и могут быть преобразованы в int
                if (xValue != null && yValue != null &&
                    int.TryParse(xValue.ToString(), out int x) &&
                    int.TryParse(yValue.ToString(), out int y))
                {
                    xList.Add(x);
                    yList.Add(y);
                }
            }

            // Создаем объект графика
            Graf graf = new Graf();
            graf.X = xList.ToArray();
            graf.Y = yList.ToArray();

            // Определяем Type
            if (radioButton1.Checked)
                graf.Type = 0;
            else if (radioButton2.Checked)
                graf.Type = 1;

            // Определяем Style
            if (radioButton3.Checked)
                graf.Style = 0;
            else if (radioButton4.Checked)
                graf.Style = 1;

            graf.Draw(pictureBox1);

        }

        private void radioButton2_CheckedChanged(object sender, EventArgs e)
        {

        }

        private void radioButton2_CheckedChanged_1(object sender, EventArgs e)
        {

        }

        private void radioButton3_CheckedChanged(object sender, EventArgs e)
        {

        }

        private void radioButton1_CheckedChanged_1(object sender, EventArgs e)
        {

        }

        private void radioButton4_CheckedChanged(object sender, EventArgs e)
        {

        }
    }
}
