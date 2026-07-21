FROM nginx:1.28.0-alpine3.21
RUN apk add --no-cache openssl
COPY nginx.conf /etc/nginx/nginx.conf.template
COPY entrypoint.sh /sandbox-entrypoint.sh
RUN chmod 0555 /sandbox-entrypoint.sh && mkdir -p /etc/nginx/tls && chown -R nginx:nginx /etc/nginx/tls
EXPOSE 8443
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
  CMD wget --no-check-certificate -q -O /dev/null https://127.0.0.1:8443/healthz || exit 1
ENTRYPOINT ["/sandbox-entrypoint.sh"]
