import React from "react";
import axios from "axios";
import { useEffect, useRef } from "react";
import "./App.css";
import Table from "./component/Table";
import Chart from "./component/Chart";
import Price from "./component/Price";

function App() {
  // 默认设置为null，否则连接不到server也会显示部分画面
  const [data, setData] = React.useState(null);
  const url = "http://127.0.0.1:8000";

  const GetData = async () => {
    axios
      .get(url)
      .then((res) => {
        setData(res.data);
      })
      .catch((error) => {
        alert("Can not connect to server!");
      });
  };

  const divRef = useRef(null);
  useEffect(() => {
    if (divRef.current) {
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
