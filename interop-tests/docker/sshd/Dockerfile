FROM ubuntu:22.04
RUN apt-get update && apt-get install -y openssh-server && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /run/sshd && mkdir -p /root/.ssh && chmod 700 /root/.ssh
RUN useradd -m -s /bin/bash legoflow && echo "legoflow:legoflow" | chpasswd
RUN ssh-keygen -A
# Force password auth and disable problematic options
RUN sed -i 's/^#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config
RUN sed -i 's/^#KbdInteractiveAuthentication yes/KbdInteractiveAuthentication no/' /etc/ssh/sshd_config
RUN sed -i 's/^#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config
RUN sed -i 's/^UsePAM.*/UsePAM no/' /etc/ssh/sshd_config
EXPOSE 22
ENTRYPOINT ["/usr/sbin/sshd", "-D", "-p", "22"]
