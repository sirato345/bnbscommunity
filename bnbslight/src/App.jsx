import React from "react";
import { useEffect, useRef } from "react";
import "./App.css";
import Table from "./component/Table";
import Chart from "./component/Chart";
import Price from "./component/Price";
import Papa from "papaparse";
import { useWorker } from "@koale/useworker";

function App() {
  // 默认设置为null，否则连接不到server也会显示部分画面
  const [data, setData] = React.useState(null);
  // const [workStatus, setWorkStatus] = useState(false);

  const TOTAL_COUNT = 21000000;

  const processCsvData = (csvData) => {
    let processResult = [];
    for (let i = 0; i < csvData.length - 1; i++) {
      let line = [];
      line.push(i + 1);
      line.push(csvData[i].HolderAddress);
      // 有逗号分割的，是字符串，没有分割的，被解析成数字类型，所以不加String会出错
      let count = Math.floor(parseFloat(String(csvData[i].Balance).replace(",", "")));
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
              console.log(results.data);
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
      GetData();
    }
  }, []);

  return (
    <div ref={divRef}>
      {data && (
        <div>
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
      )}
    </div>
  );
}

export default App;
