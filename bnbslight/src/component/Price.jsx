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
    "https://www.mexc.com/api/dex/v1/onchain/get_token_price_info?chain_id=56&token_cas=0xc07ef1c7af6112c34a110809c6c8efb343e63a64";
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
    const cacheBust = `?nocache=${Date.now()}`;
    const endpoints = [
      `https://allorigins.hexlet.app/raw?url=${encodeURIComponent(
        "https://api.dexscreener.com/latest/dex/tokens/0xc07ef1c7af6112c34a110809c6c8efb343e63a64" + cacheBust
      )}`,
      `https://allorigins.hexlet.app/raw?url=${encodeURIComponent(
        BNBs_PRICE_API + cacheBust
      )}`,
    ];

    for (const endpoint of endpoints) {
      try {
        const res = await instance.get(endpoint, { timeout: 15000 });
        const payload =
          typeof res.data === "string" ? JSON.parse(res.data) : res.data;

        let bnbsPrice = null;
        let marketCap = null;

        if (payload?.pairs?.[0]) {
          bnbsPrice = payload.pairs[0].priceUsd;
          marketCap = payload.pairs[0].marketCap;
        } else {
          const tokenData = payload?.data?.token_list?.[0] ?? payload?.data ?? payload;
          bnbsPrice = tokenData?.price ?? tokenData?.token_price;
          marketCap = tokenData?.market_cap ?? tokenData?.circulate_mkt_cap;
        }

        const normalizedPrice = Number(bnbsPrice);
        const normalizedMarketCap = Math.trunc(Number(marketCap ?? 0));

        if (Number.isFinite(normalizedPrice) && Number.isFinite(normalizedMarketCap)) {
          const displayPrice = normalizedPrice.toFixed(6);
          console.log("bnbsPrice:" + displayPrice);
          console.log("marketCap:" + normalizedMarketCap);
          return [displayPrice, normalizedMarketCap];
        }
      } catch (error) {
        console.warn("BNBs price request failed:", endpoint, error);
      }
    }

    throw new Error("Unable to load BNBs price data");
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