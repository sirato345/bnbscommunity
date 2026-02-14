import React from "react";
import axios from "axios";
import { useEffect, useRef, useState } from "react";
import BNBLogo from "../image/BNB.jpg";
import BNBsLogo from "../image/BNBs.svg";
import "./Price.css";
import { BrowserView, MobileView } from "react-device-detect";

function Price() {
  const [bnbPriceForm, setBNBPrice] = React.useState(null);
  const [bnbsPriceForm, setBNBsPrice] = React.useState(null);
  const [rateForm, setRate] = React.useState(null);
  const [marketCapForm, setMarketCap] = React.useState(null);
  const [isUpdating, setIsUpdating] = useState(false); // 添加加载状态

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
    setIsUpdating(true); // 开始加载
    try {
      const bnbPrice = await getBNBPrice();
      const [bnbsPrice, marketCap] = await getBNBsInfo();
      setBNBPrice(bnbPrice);
      setBNBsPrice(bnbsPrice);
      setMarketCap(marketCap);
      setRate(Math.trunc(bnbPrice / bnbsPrice));
    } catch (error) {
      console.error("更新失败:", error);
    } finally {
      setIsUpdating(false); // 结束加载
    }
  };

  const getBNBPrice = async () => {
    const res = await instance.get(BNB_PRICE_API);
    const bnbPrice = Number(res.data["price"]).toFixed(2);
    return bnbPrice;
  };

  const getBNBsInfo = async () => {
    const random = Math.random();
    const allOriginsUrl1 = `https://allorigins.hexlet.app/raw?url=${encodeURIComponent(
      BNBs_PRICE_API + `?nocache=${random}`
    )}`;
    const allOriginsUrl2 = `https://api.allorigins.win/get?url=${encodeURIComponent(
      BNBs_PRICE_API + `?nocache=${random}`
    )}`;
    try {
      const res = await instance.get(allOriginsUrl1);
      console.log("✅ 成功进入 then");
      const contents = JSON.parse(JSON.stringify(res.data.data));
      const bnbsPrice = contents.token_price.toFixed(6);
      const marketCap = Math.trunc(contents.circulate_mkt_cap);

      console.log("bnbsPrice:" + bnbsPrice);
      console.log("marketCap:" + marketCap);
      return [bnbsPrice, marketCap];
    } catch (error) {
      console.log("❌ 进入 catch");
      const res = await instance.get(allOriginsUrl2);
      const contents = JSON.parse(JSON.stringify(res.data.data));
      const bnbsPrice = contents.token_price.toFixed(6);
      const marketCap = Math.trunc(contents.circulate_mkt_cap);

      console.log("bnbsPrice:" + bnbsPrice);
      console.log("marketCap:" + marketCap);
      return [bnbsPrice, marketCap];
    }
  };

  const initOnce = useRef(false);
  useEffect(() => {
    if (initOnce.current) {
      return;
    }
    refresh();
    initOnce.current = true;
  }); // 添加空依赖数组

  return (
    <div>
      <BrowserView>
        <table className="Price-table">
          <tbody> {/* 添加 tbody */}
            <tr className="Price-tr2"></tr>
            <tr className="Price-tr">
              <td className="Price-td">
                <img src={BNBLogo} alt="BNBLogo" class="circle-image"></img>
                <span className="Price-span"> BNB</span>
              </td>
              <td>
                <span className="Price-span4">
                  {bnbPriceForm === null ? "update" : bnbPriceForm} $
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td className="Price-td">
                <img src={BNBsLogo} alt="BNBsLogo" class="circle-image"></img>
                <span className="Price-span"> BNBs</span>
              </td>
              <td>
                <span className="Price-span4">
                  {bnbsPriceForm === null ? "update" : bnbsPriceForm} $
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td>
                <span className="Price-span2">1 BNB = </span>
              </td>
              <td>
                <span>
                  {rateForm === null || isNaN(rateForm) ? "update" : rateForm}{" "}
                  BNBs
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td>
                <span className="Price-span3">MarketCap</span>
              </td>
              <td>
                <span>{marketCapForm === null ? "update" : marketCapForm} $</span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td></td>
              <td className="Price-td2">
                <button 
                  onClick={refresh} 
                  className={`Price-search-btn ${isUpdating ? 'loading' : ''}`}
                  disabled={isUpdating}
                >
                  {isUpdating ? (
                    <span className="spinner"></span>
                  ) : (
                    "Update"
                  )}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </BrowserView>
      <MobileView>
        <table className="Price-table-mobile">
          <colgroup>
            <col className="Price-col1-mobile"></col>
            <col className="Price-col2-mobile"></col>
          </colgroup>
          <tbody> {/* 添加 tbody */}
            <tr className="Price-tr2-mobile"></tr>
            <tr className="Price-tr">
              <td className="Price-td-mobile">
                <img src={BNBLogo} alt="BNBLogo" class="circle-image"></img>
                <span className="Price-span"> BNB</span>
              </td>
              <td>
                <span className="Price-span4">
                  {bnbPriceForm === null ? "update" : bnbPriceForm} $
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td className="Price-td-mobile">
                <img src={BNBsLogo} alt="BNBsLogo" class="circle-image"></img>
                <span className="Price-span"> BNBs</span>
              </td>
              <td>
                <span className="Price-span4">
                  {bnbsPriceForm === null ? "update" : bnbsPriceForm} $
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td>
                <span className="Price-span2">1 BNB = </span>
              </td>
              <td>
                <span>
                  {rateForm === null || isNaN(rateForm) ? "update" : rateForm}{" "}
                  BNBs
                </span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td>
                <span className="Price-span3">MarketCap</span>
              </td>
              <td>
                <span>{marketCapForm === null ? "update" : marketCapForm} $</span>
              </td>
            </tr>
            <tr className="Price-tr">
              <td></td>
              <td className="Price-td2">
                <button 
                  onClick={refresh} 
                  className={`Price-search-btn-mobile ${isUpdating ? 'loading' : ''}`}
                  disabled={isUpdating}
                >
                  {isUpdating ? (
                    <span className="spinner"></span>
                  ) : (
                    "Update"
                  )}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </MobileView>
    </div>
  );
}

export default Price;