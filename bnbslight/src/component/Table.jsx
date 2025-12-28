import "./Table.css";
import React from "react";
import { useEffect, useRef } from "react";
import { BrowserView, MobileView  } from "react-device-detect"

function Table(props) {
  const [data, setData] = React.useState([]);

  const POOL = ["0x74716187c587866ec151990e2f22806a160493f4"];

  const CREATOR = ["0xac0589a2a2015b862b33ae61fa8bbebd94d33d30"];

  const handleSubmit = (e) => {
    e.preventDefault(); // 阻止表单默认的页面刷新行为，即导航
    const form = e.target;
    const formData = new FormData(form);
    var address = formData.get("address");
    if (address === "") {
      setData(props.data);
      return;
    }
    const filteredData = props.data.filter((item) => {
      if (item[1].includes(address)) {
        return true;
      }
      return false;
    });
    setData(filteredData);
    // form.reset();// 重置表单，form内容清空
  };

  const divRef = useRef(null);
  useEffect(() => {
    if (divRef.current) {
      setData(props.data);
    }
  }, [props.data]);

  return (
    <div ref={divRef}>
      <form onSubmit={handleSubmit} method="post">
        <BrowserView>
          <table className="Table-search-area">
            <tr className="Table-search-tr">
              <td className="Table-total1">
                <span>BNBs total holders: {props.data.length}</span>
              </td>
              <td rowSpan="2" className="Table-search-td1">
                <input
                  type="text"
                  name="address"
                  className="Table-search-input"
                  placeholder="33d30"
                ></input>
              </td>
              <td rowSpan="2" className="Table-search-td2">
                <button type="submit" className="Table-search-btn">
                  Search
                </button>
              </td>
            </tr>
            <tr className="Table-search-tr">
              <td className="Table-total2">
                <span>BNBs total amount: 21000000</span>
              </td>
            </tr>
          </table>

          <div>
            <table className="Table-table">
              <thead className="Table-th">
                <th className="Table-th1">No.</th>
                <th className="Table-th2">Address</th>
                <th className="Table-th3">Amount</th>
                <th className="Table-th4">Holding Ratio</th>
                <th className="Table-th5">Comment</th>
              </thead>
              {data.map((item, index) => (
                <tr className="Table-tr" key={index}>
                  <td className="Table-td1">{item[0]}</td>
                  <td className="Table-td2">{item[1]}</td>
                  <td className="Table-td3">{item[2]}</td>
                  <td className="Table-td4">{item[3]}</td>
                  <td className="Table-td5">
                    {POOL.includes(item[1]) ? "LP" : ""}
                    {CREATOR.includes(item[1]) ? "Creator" : ""}
                  </td>
                </tr>
              ))}
            </table>
          </div>
        </BrowserView>
        <MobileView>
          <table className="Table-search-area">
            <tr className="Table-search-tr">
              <td colspan="2" className="Table-total1-mobile">
                <span>BNBs total holders: {props.data.length}</span>
              </td>
            </tr>
            <tr className="Table-search-tr">
              <td colspan="2" className="Table-total2-mobile">
                <span>BNBs total amount: 21000000</span>
              </td>
            </tr>
            <tr className="Table-search-tr">
              <td className="Table-search-td1">
                <input
                  type="text"
                  name="address"
                  className="Table-search-input-mobile"
                  placeholder="33d30"
                ></input>
              </td>
              <td className="Table-search-td2">
                <button type="submit" className="Table-search-btn">
                  Search
                </button>
              </td>
            </tr>
          </table>

          <div>
            <table className="Table-table-mobile">
              <thead className="Table-th-mobile">
                <th className="Table-th1-mobile">No.</th>
                <th className="Table-th2-mobile">Address</th>
                <th className="Table-th3-mobile">Amount</th>
              </thead>
              {data.map((item, index) => (
                <tr className="Table-tr" key={index}>
                  <td className="Table-td1-mobile">{Number(item[0])}</td>
                  <td className="Table-td2-mobile">{item[1]}</td>
                  <td className="Table-td3-mobile">{item[2]}</td>
                </tr>
              ))}
            </table>
          </div>
        </MobileView>
      </form>
    </div>
  );
}

export default Table;
