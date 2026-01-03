import React from "react";
import { useEffect, useRef } from "react";
import "./App.css";
import Table from "./component/Table";
import Chart from "./component/Chart";
import Price from "./component/Price";
import Papa from "papaparse";
import { useWorker } from "@koale/useworker";
import { BrowserView, MobileView } from "react-device-detect";
import axios from "axios";

function App() {
  // 默认设置为null，否则连接不到server也会显示部分画面
  const [data, setData] = React.useState(null);
  const [chartFlg, setChartFlg] = React.useState(10);
  // const [workStatus, setWorkStatus] = useState(false);

  const API_BASE_URL = "https://server.bnbscommunity.com";
  // const API_BASE_URL = "http://localhost:8000";
  const TOTAL_COUNT = 21000000;

  // 定义回调函数，接收子组件数据
  const onGetChartFLg = (chartFlg) => {
    setChartFlg(chartFlg);
  };

  const processCsvData = (csvData) => {
    let processResult = [];
    for (let i = 0; i < csvData.length - 1; i++) {
      let line = [];
      line.push(i + 1);
      line.push(csvData[i].HolderAddress);
      // 有逗号分割的，是字符串，没有分割的，被解析成数字类型，所以不加String会出错
      let count = Math.floor(
        parseFloat(String(csvData[i].Balance).replace(",", ""))
      );
      line.push(count);
      let percent = (count / TOTAL_COUNT) * 100;
      line.push(String(percent.toFixed(5)) + " %");
      processResult.push(line);
    }
    return processResult;
  };

  // 异步处理启动
  useWorker(processCsvData);

  const divRef = useRef(null);
  useEffect(() => {
    if (divRef.current) {
      const GetData = async () => {
        try {
          const response = await fetch(
            "export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv"
          );
          if (!response.ok) {
            console.log(`1.Can not read csv file! status: ${response.status}`);
          }

          const csvText = await response.text();

          Papa.parse(csvText, {
            header: true,
            dynamicTyping: true,
            complete: (results) => {
              // setWorkStatus(true);
              const processResult = processCsvData(results.data);
              // setWorkStatus(false);
              setData(processResult);
            },
            error: (err) => {
              console.log("2.Can not parse csv file: " + err.message);
            },
          });
          console.log("4.Reading csv file succeed!");
        } catch (err) {
          console.log("3.Failed reading csv file: " + err.message);
        }
      };

      axios
        .get(API_BASE_URL)
        .then((res) => {
          setData(res.data);
        })
        .catch((error) => {
          alert("Can not connect to server!");
          GetData();
        });
    }
  }, []);

  return (
    <div ref={divRef}>
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
                      <Chart data={data} userCount={10} chartFlg={chartFlg} callbacks={onGetChartFLg}></Chart>
                    </td>
                  ) : null}
                  {chartFlg === 50 ? (
                    <td className="App-td-mobile">
                      <Chart data={data} userCount={50} chartFlg={chartFlg} callbacks={onGetChartFLg}></Chart>
                    </td>
                  ) : null}
                  {chartFlg === 100 ? (
                    <td className="App-td-mobile">
                      <Chart data={data} userCount={100} chartFlg={chartFlg} callbacks={onGetChartFLg}></Chart>
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
