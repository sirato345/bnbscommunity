import "./Chart.css";
import { PieChart, Pie, Cell, ResponsiveContainer } from "recharts";
import { BrowserView, MobileView } from "react-device-detect";

function Chart(props) {
  var data;
  var data1;
  var data2;
  var data3;

  var value1 = props.data
    .slice(0, props.userCount)
    .reduce((total, item) => total + item[2], 0);
  var value2 = 21000000 - value1;

  data1 = [
      { name: "Top 10", value: value1, color: "#FFBB28" },
      { name: "Others", value: value2, color: "#998ae5ff" },
  ];

  data2 = [
    { name: "Top 50", value: value1, color: "#00C49F" },
    { name: "Others", value: value2, color: "#998ae5ff" },
  ];

  data3 = [
    { name: "Top 100", value: value1, color: "#0088FE" },
    { name: "Others", value: value2, color: "#998ae5ff" },
  ];
  
  if (props.userCount === 10) {
    data = data1;
  } else if (props.userCount === 50) {
    data = data2;
  } else {
    data = data3;
  }

  const RADIAN = Math.PI / 180;

  // 自定义居中标签渲染函数
  const renderCustomizedLabel = ({
    cx,
    cy,
    midAngle,
    innerRadius,
    outerRadius,
    percent,
    index,
    name,
  }) => {
    // 计算标签位置 - 在扇形的中间（0.7是文字距离边界的距离）
    const radius = innerRadius + (outerRadius - innerRadius) * 0.7;
    const x = cx + radius * Math.cos(-midAngle * RADIAN);
    const y = cy + radius * Math.sin(-midAngle * RADIAN);

    return (
      <text
        x={x}
        y={y}
        fill="black"
        textAnchor="middle"
        dominantBaseline="central"
        style={{
          fontSize: "14px",
          fontWeight: "bold",
        }}
      >
        {`${(percent * 100).toFixed(0)}%`}
      </text>
    );
  };

  return (
    <div>
      <BrowserView>
        <div className="Chart-div1">
          <div className="Chart-div2">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  cx="50%"
                  cy="50%"
                  innerRadius={0}
                  outerRadius={80}
                  labelLine={false}
                  label={renderCustomizedLabel}
                  paddingAngle={0}
                  dataKey="value"
                  startAngle={90}
                  endAngle={450}
                >
                  {data.map((entry, index) => (
                    <Cell
                      key={entry.name}
                      fill={entry.color}
                      stroke="#fff"
                      strokeWidth={2}
                    />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="Chart-div3">
            <p className="Chart-lable">{data[0].name}</p>
            <div className="Chart-square" style={{backgroundColor: data[0].color}}></div>
          </div>
        </div>
      </BrowserView>
      <MobileView>
        <div className="Chart-div1-mobile">
          <div className="Chart-div2-mobile">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  cx="50%"
                  cy="50%"
                  innerRadius={0}
                  outerRadius={66}
                  labelLine={false}
                  label={renderCustomizedLabel}
                  paddingAngle={0}
                  dataKey="value"
                  startAngle={90}
                  endAngle={450}
                >
                  {data.map((entry, index) => (
                    <Cell
                      key={entry.name}
                      fill={entry.color}
                      stroke="#fff"
                      strokeWidth={2}
                    />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="Chart-div3-mobile" style={{border: props.chartFlg === 10 ? '1px solid black' : 'none'}}>
            <label className="Chart-lable" onClick={() => props.callbacks(10)}>{data1[0].name}</label>
            <div className="Chart-square-mobile" style={{backgroundColor: data1[0].color}}></div>
          </div>
          <div className="Chart-div3-mobile" style={{border: props.chartFlg === 50 ? '1px solid black' : 'none'}}>
            <label className="Chart-lable" onClick={() => props.callbacks(50)}>{data2[0].name}</label>
            <div className="Chart-square-mobile" style={{backgroundColor: data2[0].color}}></div>
          </div>
          <div className="Chart-div3-mobile" style={{border: props.chartFlg === 100 ? '1px solid black' : 'none'}}>
            <label className="Chart-lable" onClick={() => props.callbacks(100)}>{data3[0].name}</label>
            <div className="Chart-square-mobile" style={{backgroundColor: data3[0].color}}></div>
          </div>
        </div>
      </MobileView>
    </div>
  );
}

export default Chart;
