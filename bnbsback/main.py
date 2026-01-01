from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import csv
import shutil

app = FastAPI()

origins = [
    "https://localhost:3000",
    "https://server.bnbscommunity.com",
    "https://bnbchain.bnbscommunity.com",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

totalCount = 21000000

@app.get("/")
def Hello():
    datalist = []
    with open("csv/export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv") as csvfile:
        reader = csv.reader(csvfile)

        i = 0
        for line in reader:
            if (i == 0):
                i = i + 1
                continue
            
            newline = []
            newline.append(i)
            newline.append(line[0])

            count = int(float(line[1].replace(",", "")))
            newline.append(count)

            percent = (count / totalCount) * 100
            newline.append(f"{percent:.5f}%")

            datalist.append(newline)
            i = i + 1

    return datalist

@app.post("/upload")
async def Upload(file: UploadFile = File(...)):        
    # 保存文件
    with open("csv/export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv", "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
