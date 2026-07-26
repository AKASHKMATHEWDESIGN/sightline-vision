FROM python:3.11-slim

WORKDIR /app

# OpenCV needs these system libraries even in "headless" mode.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libgl1 curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .
COPY index.html .

# Download the face detector (Caffe SSD/ResNet-10) and the 68-point
# facemark LBF landmark model used for head-pose estimation.
RUN mkdir -p /app/models && \
    curl -fL -o /app/models/deploy.prototxt \
      https://raw.githubusercontent.com/opencv/opencv/master/samples/dnn/face_detector/deploy.prototxt && \
    curl -fL -o /app/models/res10_300x300_ssd_iter_140000.caffemodel \
      https://raw.githubusercontent.com/opencv/opencv_3rdparty/dnn_samples_face_detector_20170830/res10_300x300_ssd_iter_140000.caffemodel && \
    curl -fL -o /app/models/lbfmodel.yaml \
      https://raw.githubusercontent.com/kurnianggoro/GSOC2017/master/data/lbfmodel.yaml

ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "gunicorn --bind 0.0.0.0:${PORT} --workers 1 --threads 4 --timeout 120 app:app"]
