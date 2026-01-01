import React from "react";
import axios from "axios";
import { useEffect, useRef } from "react";
import BNBLogo from "../image/BNB.jpg";
import BNBsLogo from "../image/BNBs.jpg";
import "./Price.css";
import { BrowserView, MobileView } from "react-device-detect";

function Price() {
  const [bnbsPrice, setBNBsPrice] = React.useState(null);
  const [marketCap, setMarketCap] = React.useState(null);
  const [bnbPrice, setBNBPrice] = React.useState(null);
  const [rate, setRate] = React.useState(null);

  const BNBs_PRICE_API =
    "https://www.mexc.com/api/dex/v1/data/get_market_info?chain_id=56&pair_ca=0x74716187C587866EC151990e2f22806a160493F4&token_ca=0xC07ef1C7af6112C34A110809C6c8Efb343e63A64";
  const BNB_PRICE_API =
    "https://api.binance.com/api/v3/ticker/price?symbol=BNBUSDT";
  // 调整axios配置
  const instance = axios.create({
    timeout: 30000, // 增加超时时间
    headers: {
      "Content-Type": "application/json",
    },
  });

  const getBNBsPrice = async () => {
    // 方法2：使用allOrigins（纯前端）
    const fetchWithAllOrigins = async (url) => {
      const allOriginsUrl = `https://api.allorigins.win/get?url=${encodeURIComponent(
        url
      )}`;

      const response = await instance.get(allOriginsUrl);
      const contents = JSON.parse(response.data.contents);
      setBNBsPrice(contents.data.token_price.toFixed(6));
      setMarketCap(Math.trunc(contents.data.circulate_mkt_cap));
      // allOrigins返回的数据结构是 { contents: "..." }
      return contents;
    };

    // 示例：获取GitHub用户信息
    fetchWithAllOrigins(BNBs_PRICE_API).then((data) => console.log(data));
  };

  const getBNBPrice = async () => {
    instance.get(BNB_PRICE_API).then((res) => {
      console.log("Get BNB price:" + res.data["price"]);
      setBNBPrice(Number(res.data["price"]).toFixed(2));
      setRate(Math.trunc(bnbPrice / bnbsPrice));
    });
  };

  const refresh = (e) => {
    setBNBsPrice("update");
    setMarketCap("update");
    setBNBPrice("update");
    setRate("update");
    getBNBsPrice();
    getBNBPrice();
  };

  const divRef = useRef(null);
  useEffect(() => {
    if (divRef.current) {
      getBNBsPrice();
      getBNBPrice();
    }
  });

  return (
    <div ref={divRef}>
      <BrowserView>
        <table className="Price-table">
          <tr className="Price-tr2"></tr>
          <tr className="Price-tr">
            <td className="Price-td">
              <img src={BNBLogo} alt="BNBLogo"></img>
              <span className="Price-span"> BNB</span>
            </td>
            <td>
              <span className="Price-span4">{bnbPrice === null ? "update" : bnbPrice} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td className="Price-td">
              <img src={BNBsLogo} alt="BNBsLogo"></img>
              <span className="Price-span"> BNBs</span>
            </td>
            <td>
              <span className="Price-span4">{bnbsPrice === null ? "update" : bnbsPrice} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span2">1 BNB = </span>
            </td>
            <td>
              <span>{(bnbsPrice === null || isNaN(bnbsPrice)) ? "update" : rate} BNBs</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span3">MarketCap</span>
            </td>
            <td>
              <span>{bnbsPrice === null ? "update" : marketCap} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td></td>
            <td className="Price-td2">
              <button onClick={refresh} className="Price-search-btn">
                Update
              </button>
            </td>
          </tr>
        </table>
      </BrowserView>
      <MobileView>
        <table className="Price-table">
          <tr className="Price-tr2"></tr>
          <tr className="Price-tr">
            <td className="Price-td">
              <img src={BNBLogo} alt="BNBLogo"></img>
              <span className="Price-span"> BNB</span>
            </td>
            <td>
              <span className="Price-span4">{bnbPrice === null ? "update" : bnbPrice} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td className="Price-td">
              <img src={BNBsLogo} alt="BNBsLogo"></img>
              <span className="Price-span"> BNBs</span>
            </td>
            <td>
              <span className="Price-span4">{bnbsPrice === null ? "update" : bnbsPrice} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span2">1 BNB = </span>
            </td>
            <td>
              <span>{bnbsPrice === null ? "update" : rate} BNBs</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span3">MarketCap</span>
            </td>
            <td>
              <span>{bnbsPrice === null ? "update" : marketCap} $</span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td></td>
            <td className="Price-td2">
              <button onClick={refresh} className="Price-search-btn-mobile">
                Update
              </button>
            </td>
          </tr>
        </table>
      </MobileView>
    </div>
  );
}

export default Price;
