FROM ubuntu:22.04
RUN apt-get update && apt-get install -y pure-ftpd-common pure-ftpd-dirsync pure-ftpd-modules && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /srv/ftp/pub && chmod a+w /srv/ftp/pub

# Pure-FTPd config
RUN printf '%s\n' \
    'PureDB                    /etc/pure-ftpd/pureftpd-pdb.conf' \
    'NoAnonymous               no' \
    'AnonymousCanCreateDirs    yes' \
    'AnonymousCantUpload       no' \
    'AnonymousCourseShell      /bin/false' \
    'AnonymousConsole          yes' \
    'AnonymousLogin            yes' \
    'AnonymousMustChangePasswd no' \
    'AnonymousRatio            10 0' \
    'AnonymousBandwidth        1000 0' \
    'AutoRename                no' \
    'ChownByGroup              no' \
    'CreateHomeDir             yes' \
    'DisplayDotFiles           yes' \
    'Don'tResolve             yes' \
    'DontDiffFtpdandTFTPdir    yes' \
    'EACH_DEFAULT_QUOTA        0' \
    'EncodePrivPGP             no' \
    'ForcePassiveIP            ' \
    'FrequentRenewDNS          yes' \
    'FSCharset                 UTF-8' \
    'FTPDataPortIP             0.0.0.0' \
    'ForcePassiveIP            127.0.0.1' \
    'FortunesFile              /usr/share/fortune' \
    'FollowDirectorySymlinks   no' \
    'FollowSymbolicLinks       no' \
    'HomeUserGroup             101:101' \
    'ImplicitTLS               no' \
    'IPV4Only                  no' \
    'MaxClientPerIP            0' \
    'MaxDiskUsage              99' \
    'MaxIdleTime               15' \
    'MinUID                    0' \
    'PIDFile                   /var/run/pure-ftpd.pid' \
    'ProhibitDotFilesRead      no' \
    'ProhibitDotFilesWrite     no' \
    'PAMAuthentication         no' \
    'PureDB                    /etc/pure-ftpd/pureftpd.pdb' \
    'QualifyRenaming           no' \
    'Quota                   1000:10' \
    'ReadOnly                  no' \
    'RestrictOnlyAnonymous     no' \
    'SeparateSessions          no' \
    'SessionCache              no' \
    'SyslogFacility            daemon' \
    'ThrottleDNS               no' \
    'TrustedGID                0' \
    'TrustedIP                 127.0.0.1' \
    'UnixAuthentication        no' \
    'UnixSettings              /etc/pure-ftpd/common.conf' \
    'UnicodeFilterFile         /etc/pure-ftpd/unicode.map' \
    'UploadDiskUsage           0' \
    'VerboseLogin              no' \
    > /etc/pure-ftpd/conf/README

# Configure passive mode ports
RUN printf '30000\n30009\n' > /etc/pure-ftpd/conf/PassivePortRange

# Config directory for startup options  
RUN mkdir -p /etc/pure-ftpd/conf

# The main config
RUN printf '%s\n' \
    'AnonymousEnable           Yes' \
    'AnonymousOnly             No' \
    'AnonymousCantUpload       No' \
    'AnonymousCanCreateDirs    Yes' \
    'AnonymousMustChangePasswd No' \
    'AnonymousRatio            10 0' \
    'AnonymousBandwidth        1000 0' \
    'AnonymousLogin            Yes' \
    'AnonymousUser             nobody' \
    'ChrootEveryone            No' \
    'ClientCharset             UTF-8' \
    'DontResolve               Yes' \
    'ForcePassiveIP            127.0.0.1' \
    'IPv4Only                  Yes' \
    'MaxClientsNumber          50' \
    'MaxClientsPerIP           10' \
    'MaxIdleTime               15' \
    'NoChmod                   Yes' \
    'NoDump                    Yes' \
    'PassivePortRange          30000 30009' \
    'PAMAuthentication         no' \
    'ProhibitDotFilesWrite     No' \
    'ProhibitDotFilesRead      No' \
    'PureDB                    /etc/pure-ftpd/pureftpd.pdb' \
    'RestrictedUser            ' \
    'ReadOnly                  No' \
    'SyslogFacility            daemon' \
    'TrustedGID                100' \
    'TrustedIP                 127.0.0.1,::1' \
    'UnixAuthentication        no' \
    'UploadFileLimit           0' \
    'UploadDir                 ' \
    'UploadRatio               10 0' \
    'UploadBandwidth           1000 0' \
    'VerboseLogin              No' \
    'VirtualChroot             no' \
    'VirtualRoot               no' \
    > /etc/pure-ftpd/conf/pure-ftpd.conf

EXPOSE 21 30000-30009
CMD ["pure-ftpd", "-l", "pam", "-E", "-A", "-B"]
