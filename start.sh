# 全栈一键启动见仓库根目录 run_local.sh（含 RabbitMQ + worker.py）
docker compose up -d
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

##前端
cd frontend
npm run dev

##java
cd backend-java    
mvn spring-boot:run

##python
cd D:\novel\writer-python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000