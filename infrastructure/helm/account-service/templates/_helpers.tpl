{{/*
Expand the name of the chart.
*/}}
{{- define "account-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "account-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "account-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "account-service.labels" -}}
helm.sh/chart: {{ include "account-service.chart" . }}
{{ include "account-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: account-service
app.kubernetes.io/component: backend
{{- end }}

{{/*
Selector labels
*/}}
{{- define "account-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "account-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "account-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "account-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the image name
*/}}
{{- define "account-service.image" -}}
{{- $registry := .Values.global.imageRegistry | default .Values.image.registry -}}
{{- $repository := .Values.image.repository -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion -}}
{{- if $registry }}
{{- printf "%s/%s:%s" $registry $repository $tag }}
{{- else }}
{{- printf "%s:%s" $repository $tag }}
{{- end }}
{{- end }}

{{/*
Create image pull secrets
*/}}
{{- define "account-service.imagePullSecrets" -}}
{{- $pullSecrets := list }}
{{- if .Values.global.imagePullSecrets }}
{{- $pullSecrets = .Values.global.imagePullSecrets }}
{{- else if .Values.image.pullSecrets }}
{{- $pullSecrets = .Values.image.pullSecrets }}
{{- end }}
{{- if $pullSecrets }}
imagePullSecrets:
{{- range $pullSecrets }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create storage class
*/}}
{{- define "account-service.storageClass" -}}
{{- if .Values.global.storageClass }}
{{- .Values.global.storageClass }}
{{- else if .Values.persistence.storageClass }}
{{- .Values.persistence.storageClass }}
{{- else }}
{{- "standard" }}
{{- end }}
{{- end }}

{{/*
Create database host
*/}}
{{- define "account-service.databaseHost" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" (include "account-service.fullname" .) }}
{{- else }}
{{- .Values.database.host }}
{{- end }}
{{- end }}

{{/*
Create database port
*/}}
{{- define "account-service.databasePort" -}}
{{- if .Values.postgresql.enabled }}
{{- .Values.postgresql.primary.service.ports.postgresql | default 5432 }}
{{- else }}
{{- .Values.database.port }}
{{- end }}
{{- end }}

{{/*
Create database name
*/}}
{{- define "account-service.databaseName" -}}
{{- if .Values.postgresql.enabled }}
{{- .Values.postgresql.auth.database }}
{{- else }}
{{- .Values.database.name }}
{{- end }}
{{- end }}

{{/*
Create database username
*/}}
{{- define "account-service.databaseUsername" -}}
{{- if .Values.postgresql.enabled }}
{{- .Values.postgresql.auth.username }}
{{- else }}
{{- .Values.database.username }}
{{- end }}
{{- end }}

{{/*
Create database secret name
*/}}
{{- define "account-service.databaseSecretName" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" (include "account-service.fullname" .) }}
{{- else }}
{{- .Values.database.existingSecret }}
{{- end }}
{{- end }}

{{/*
Validate required values
*/}}
{{- define "account-service.validateValues" -}}
{{- if not .Values.database.host }}
{{- if not .Values.postgresql.enabled }}
{{- fail "database.host is required when postgresql.enabled is false" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create environment variables
*/}}
{{- define "account-service.env" -}}
- name: SPRING_DATASOURCE_URL
  value: {{ printf "jdbc:postgresql://%s:%s/%s" (include "account-service.databaseHost" .) (include "account-service.databasePort" .) (include "account-service.databaseName" .) | quote }}
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "account-service.databaseSecretName" . }}
      key: {{ .Values.database.secretKeys.username | default "username" }}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "account-service.databaseSecretName" . }}
      key: {{ .Values.database.secretKeys.password | default "password" }}
{{- range .Values.env }}
- name: {{ .name }}
  value: {{ .value | quote }}
{{- end }}
{{- end }}
