FROM python:3.12-slim
WORKDIR /app
COPY backend/health_api /app/health_api
RUN pip install --no-cache-dir -r /app/health_api/requirements.txt
ENV PYTHONPATH=/app
EXPOSE 8000
CMD ["sh", "-c", "uvicorn health_api.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
