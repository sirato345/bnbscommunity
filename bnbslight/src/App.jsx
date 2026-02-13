import React from "react";
import { useEffect, useRef } from "react";
import "./App.css";
import Table from "./component/Table";
import Chart from "./component/Chart";
import Price from "./component/Price";
import { BrowserView, MobileView } from "react-device-detect";
import axios from "axios";

function App() {
  // 默认设置为null，否则连接不到server也会显示部分画面
  const [data, setData] = React.useState(null);
  const [chartFlg, setChartFlg] = React.useState(10);

  // const API_BASE_URL = "https://bnbscommunity.fly.dev/csv";
  const API_BASE_URL = "http://localhost:8000/csv";

  // 调整axios配置
  const instance = axios.create({
    timeout: 20000, // 增加超时时间
    headers: {
      "Content-Type": "application/json",
    },
  });

  // 定义回调函数，接收子组件数据
  const onGetChartFLg = (chartFlg) => {
    setChartFlg(chartFlg);
  };

  const initOnce = useRef(false);
  // 在 React 组件加载时获取数据，推荐使用 useEffect 配合空依赖数组来实现。
  useEffect(() => {
    // 从后端取得CSV
    const getDataFromBack = async () => {
      const res = await instance.get(API_BASE_URL);
      setData(res.data);
    };

    if (!initOnce.current) {
      initOnce.current = true;
      getDataFromBack();
    }
  }, []); // 空数组代表只执行一次，无依赖数组则每次渲染都执行，需手动控制只执行一次

  return (
    <div>
      {data && (
        <div>
          <BrowserView>
            <div className="App-div">
              <table className="App-table">
                <tr className="App-tr">
                  <td className="App-td">
                    <Chart data={data} userCount={10}></Chart>
                  </td>
                  <td className="App-td">
                    <Chart data={data} userCount={50}></Chart>
                  </td>
                  <td className="App-td">
                    <Chart data={data} userCount={100}></Chart>
                  </td>
                  <td className="App-td4">
                    <Price></Price>
                  </td>
                </tr>
              </table>
              <Table data={data}></Table>
            </div>
          </BrowserView>
          <MobileView>
            <div className="App-div-mobile">
              <table className="App-table-mobile">
                <tr className="App-tr">
                  {chartFlg === 10 ? (
                    <td className="App-td-mobile">
                      <Chart
                        data={data}
                        userCount={10}
                        chartFlg={chartFlg}
                        callbacks={onGetChartFLg}
                      ></Chart>
                    </td>
                  ) : null}
                  {chartFlg === 50 ? (
                    <td className="App-td-mobile">
                      <Chart
                        data={data}
                        userCount={50}
                        chartFlg={chartFlg}
                        callbacks={onGetChartFLg}
                      ></Chart>
                    </td>
                  ) : null}
                  {chartFlg === 100 ? (
                    <td className="App-td-mobile">
                      <Chart
                        data={data}
                        userCount={100}
                        chartFlg={chartFlg}
                        callbacks={onGetChartFLg}
                      ></Chart>
                    </td>
                  ) : null}
                  <td className="App-td4-mobile">
                    <Price></Price>
                  </td>
                </tr>
              </table>
              <Table data={data}></Table>
            </div>
          </MobileView>
        </div>
      )}
    </div>
  );
}

export default App;
