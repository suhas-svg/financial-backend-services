FROM nginx:1.28.3-alpine3.23@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
RUN apk del --no-cache nginx-module-acme nginx-module-geoip nginx-module-image-filter nginx-module-njs nginx-module-xslt \
    && apk upgrade --no-cache \
    && apk add --no-cache --upgrade nginx openssl
COPY nginx.conf /etc/nginx/nginx.conf.template
COPY entrypoint.sh /sandbox-entrypoint.sh
RUN chmod 0555 /sandbox-entrypoint.sh && mkdir -p /etc/nginx/tls && chown -R nginx:nginx /etc/nginx/tls
EXPOSE 8443
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
  CMD wget --no-check-certificate -q -O /dev/null https://127.0.0.1:8443/healthz || exit 1
USER nginx
ENTRYPOINT ["/sandbox-entrypoint.sh"]
