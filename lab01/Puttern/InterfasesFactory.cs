using System;
using System.Collections.Generic;
using System.Text;

namespace WinFormsApp2
{
    public interface IGraph
    {
        void Draw(PictureBox pictureBox, int[] X, int[] Y);
    }

    public interface IGraphFactory
    {
        IGraph CreateSimpleGraph();
        IGraph CreateStyledGraph();
    }


    public class StepGraphFactory : IGraphFactory
    {
        public IGraph CreateSimpleGraph() => new StepSimpleGraph();
        public IGraph CreateStyledGraph() => new StepStyledGraph();
    }

    public class LineGraphFactory : IGraphFactory
    {
        public IGraph CreateSimpleGraph() => new LineSimpleGraph();
        public IGraph CreateStyledGraph() => new LineStyledGraph();
    }
}
