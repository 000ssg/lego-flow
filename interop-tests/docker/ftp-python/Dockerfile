FROM python:3.11-slim
RUN pip install pyftpdlib > /dev/null 2>&1 && mkdir -p /srv/ftp/pub
COPY server.py /srv/server.py
EXPOSE 21 30000-30009
CMD ["python", "/srv/server.py"]
