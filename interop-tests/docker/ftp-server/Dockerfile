FROM ubuntu:22.04
RUN apt-get update && apt-get install -y vsftpd && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /srv/ftp/pub && chmod a+w /srv/ftp/pub

# Configure vsftpd - use printf to write the config file
RUN printf '%s\n' \
  '# Basic settings' \
  'listen=YES' \
  'anonymous_enable=YES' \
  'local_enable=YES' \
  'write_enable=YES' \
  'anon_upload_enable=YES' \
  'anon_mkdir_write=YES' \
  'anon_other_write=YES' \
  '' \
  '# Passive mode' \
  'pasv_enable=YES' \
  'pasv_min_port=30000' \
  'pasv_max_port=30009' \
  'pasv_addr_resolve=NO' \
  'pasv_address=127.0.0.1' \
  '' \
  '# Security' \
  'anon_root=/srv/ftp' \
  'connect_from_port_20=NO' \
  'file_open_mode=0666' \
  '' \
  '# Logging' \
  'xferlog_enable=YES' \
  'xferlog_file=/var/log/vsftpd.log' \
  'xferlog_std_format=NO' \
  '' \
  '# Performance' \
  'max_clients=50' \
  'max_per_ip=10' \
  > /etc/vsftpd.conf

EXPOSE 21 30000-30009
ENTRYPOINT ["/bin/sh", "-c"]
CMD ["/usr/sbin/vsftpd /etc/vsftpd.conf; exec tail -f /dev/null"]
