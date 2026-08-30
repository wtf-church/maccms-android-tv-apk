# maccms V10 web 容器（PHP 7.2-fpm + Nginx），离线安装演示数据
FROM docker.m.daocloud.io/library/php:7.2.34-fpm-buster

ENV DEBIAN_FRONTEND=noninteractive

# PHP 7.2 已内置 pdo_mysql/mysqlnd；Debian 10 buster 已归档，切 archive.debian.org（CDN 波动大，apt 加重试）
RUN sed -i \
      -e 's|deb.debian.org|archive.debian.org|g' \
      -e 's|security.debian.org|archive.debian.org|g' \
      -e '/debian-security/d' \
      /etc/apt/sources.list \
    && printf 'Acquire::Check-Valid-Until "false";\nAcquire::Retries "10";\nAcquire::http::Timeout "60";\nAcquire::https::Timeout "60";\n' > /etc/apt/apt.conf.d/99no-valid-until \
    && apt-get update \
    && for i in 1 2 3 4 5 6 7 8 9 10; do \
         apt-get install -y --no-install-recommends -o Acquire::Check-Valid-Until=false \
           -o Acquire::Retries=20 -o Acquire::http::Timeout=90 \
           nginx curl libzip-dev unzip libpng-dev libjpeg62-turbo-dev libfreetype6-dev \
         && break \
         || { echo "apt install failed (try $i), retrying..."; sleep 5; apt-get update; }; \
       done \
    && apt-get install -y --fix-missing --no-install-recommends -o Acquire::Check-Valid-Until=false nginx curl libzip-dev unzip libpng-dev libjpeg62-turbo-dev libfreetype6-dev \
    && docker-php-source extract \
    && docker-php-ext-install pdo_mysql mysqli pcntl sysvsem \
    && docker-php-ext-configure gd --with-freetype-dir=/usr --with-jpeg-dir=/usr --with-png-dir=/usr \
    && docker-php-ext-install gd \
    && docker-php-ext-enable pdo_mysql mysqli pcntl sysvsem gd \
    && docker-php-source delete \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/www/maccms /var/log/nginx

# 拷贝 maccms 源码与演示数据
COPY src/ /var/www/maccms/
COPY initdb/ /var/www/maccms/initdb/
COPY assets/ /var/www/maccms/assets/
COPY nginx-default.conf /etc/nginx/sites-available/default

# 写权限（runtime/upload/application/extra/application/data）
RUN chmod -R 777 /var/www/maccms/runtime \
    /var/www/maccms/upload \
    /var/www/maccms/application/extra \
    /var/www/maccms/application/data \
    /var/www/maccms/application/database.php \
    && ln -sf /dev/stdout /var/log/nginx/access.log \
    && ln -sf /dev/stderr /var/log/nginx/error.log

COPY scripts/install.sh /usr/local/bin/maccms-install.sh
RUN chmod +x /usr/local/bin/maccms-install.sh

WORKDIR /var/www/maccms

# 容器启动：等 DB → 安装 → 启动 php-fpm + nginx
CMD ["/usr/local/bin/maccms-install.sh"]
