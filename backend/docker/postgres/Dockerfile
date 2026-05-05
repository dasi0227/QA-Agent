FROM pgvector/pgvector:0.7.4-pg16

RUN sed -i 's|http://deb.debian.org|http://mirrors.aliyun.com|g' /etc/apt/sources.list.d/*.sources 2>/dev/null; \
    sed -i 's|http://deb.debian.org|http://mirrors.aliyun.com|g' /etc/apt/sources.list 2>/dev/null; \
    true

RUN apt-get update && apt-get install -y --no-install-recommends \
        postgresql-server-dev-16 \
        build-essential \
        wget \
        git \
        ca-certificates \
    && wget -q -O - http://www.xunsearch.com/scws/down/scws-1.2.3.tar.bz2 | tar xjf - \
    && cd scws-1.2.3 && ./configure && make && make install \
    && cd .. && rm -rf scws-1.2.3 \
    && git clone --depth 1 https://github.com/amutu/zhparser.git /tmp/zhparser \
    && cd /tmp/zhparser && make && make install \
    && apt-get purge -y postgresql-server-dev-16 build-essential wget git \
    && apt-get autoremove -y \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/zhparser
