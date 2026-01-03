import React from "react";
import axios from "axios";
import { useEffect, useRef } from "react";
import BNBLogo from "../image/BNB.jpg";
import BNBsLogo from "../image/BNBs.jpg";
import "./Price.css";
import { BrowserView, MobileView } from "react-device-detect";

function Price() {
  const [bnbPrice, setBNBPrice] = React.useState(null);
  const [bnbsPrice, setBNBsPrice] = React.useState(null);
  const [rate, setRate] = React.useState(null);
  const [marketCap, setMarketCap] = React.useState(null);

  const BNBs_PRICE_API =
    "https://www.mexc.com/api/dex/v1/data/get_market_info?chain_id=56&pair_ca=0x74716187C587866EC151990e2f22806a160493F4&token_ca=0xC07ef1C7af6112C34A110809C6c8Efb343e63A64";
  const BNB_PRICE_API =
    "https://api.binance.com/api/v3/ticker/price?symbol=BNBUSDT";
  // 调整axios配置
  const instance = axios.create({
    timeout: 20000, // 增加超时时间
    headers: {
      "Content-Type": "application/json",
    },
  });

  const refresh = async () => {
    const allOriginsUrl1 = `https://corsproxy.io/get?url=${encodeURIComponent(
      BNBs_PRICE_API
    )}`;
    const allOriginsUrl2 = `https://allorigins.hexlet.app/raw?url=${encodeURIComponent(
      BNBs_PRICE_API
    )}`;
    await instance.get(BNB_PRICE_API).then((res) => {
      var bnbPriceTmp = Number(res.data["price"]).toFixed(2);
      setBNBPrice(bnbPriceTmp);
      
      instance
        .get(allOriginsUrl1)
        .then((res) => {
          console.log("✅ 成功进入 then");
          const contents = JSON.parse(JSON.stringify(res.data.data));
          var bnbsPriceTmp = contents.token_price.toFixed(6);
          setBNBsPrice(bnbsPriceTmp);
          setMarketCap(Math.trunc(contents.circulate_mkt_cap));
          setRate(Math.trunc(bnbPriceTmp / bnbsPriceTmp));
        })
        .catch((error) => {
          console.log("❌ 进入 catch");
          instance.get(allOriginsUrl2).then((res) => {
            const contents = JSON.parse(JSON.stringify(res.data.data));
            var bnbsPriceTmp = contents.token_price.toFixed(6);
            setBNBsPrice(bnbsPriceTmp);
            setMarketCap(Math.trunc(contents.circulate_mkt_cap));
            setRate(Math.trunc(bnbPriceTmp / bnbsPriceTmp));
          });
        });
    });
  };

  const divRef = useRef(null);
  const excuteOnce = useRef(false);
  useEffect(() => {
    // 防止重复请求
    if (excuteOnce.current) {
      return;
    }
    refresh();
    excuteOnce.current = true;
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
              <span className="Price-span4">
                {bnbPrice === null ? "update" : bnbPrice} $
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td className="Price-td">
              <img src={BNBsLogo} alt="BNBsLogo"></img>
              <span className="Price-span"> BNBs</span>
            </td>
            <td>
              <span className="Price-span4">
                {bnbsPrice === null ? "update" : bnbsPrice} $
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span2">1 BNB = </span>
            </td>
            <td>
              <span>
                {(rate === null || isNaN(rate)) ? "update" : rate} BNBs
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span3">MarketCap</span>
            </td>
            <td>
              <span>{marketCap === null ? "update" : marketCap} $</span>
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
        <table className="Price-table-mobile">
          <colgroup>
            <col className="Price-col1-mobile"></col>
            <col className="Price-col2-mobile"></col>
          </colgroup>
          <tr className="Price-tr2-mobile"></tr>
          <tr className="Price-tr">
            <td className="Price-td-mobile">
              <img src={BNBLogo} alt="BNBLogo"></img>
              <span className="Price-span"> BNB</span>
            </td>
            <td>
              <span className="Price-span4">
                {bnbPrice === null ? "update" : bnbPrice} $
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td className="Price-td-mobile">
              <img src={BNBsLogo} alt="BNBsLogo"></img>
              <span className="Price-span"> BNBs</span>
            </td>
            <td>
              <span className="Price-span4">
                {bnbsPrice === null ? "update" : bnbsPrice} $
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span2">1 BNB = </span>
            </td>
            <td>
              <span>
                {(rate === null || isNaN(rate)) ? "update" : rate} BNBs
              </span>
            </td>
          </tr>
          <tr className="Price-tr">
            <td>
              <span className="Price-span3">MarketCap</span>
            </td>
            <td>
              <span>{marketCap === null ? "update" : marketCap} $</span>
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
