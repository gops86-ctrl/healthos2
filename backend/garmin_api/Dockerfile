FROM python:3.12-slim

WORKDIR /app
COPY backend/garmin_adapter /app/garmin_adapter
COPY backend/garmin_api /app/garmin_api

RUN pip install --no-cache-dir -r /app/garmin_api/requirements.txt

ENV PYTHONPATH=/app
ENV GARMINTOKENS=/data/garmin_tokens
EXPOSE 8000

CMD ["sh", "-c", "uvicorn garmin_api.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
