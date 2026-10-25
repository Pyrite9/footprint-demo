# 화면 이미지를 만듭니다. 저장소 맨 위의 docker-compose.yml이 이 파일을 씁니다.
# 1단계(build)는 Node.js로 화면 파일(dist)을 만들고, 2단계는 nginx가 그 파일을 내려줍니다. 시연에는 개발 서버(npm run dev)가 필요 없습니다.

# 1단계: 화면 파일을 만듭니다.
FROM node:24-alpine AS build
WORKDIR /app

# package 파일만 먼저 복사해 설치합니다. 소스만 바뀌면 Docker가 설치 단계를 cache로 건너뜁니다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# 백엔드 주소는 빌드할 때 화면 파일 안에 들어갑니다(Vite의 VITE_ 변수). 값은 docker-compose.yml의 args에서 받습니다.
ARG VITE_API_URL=http://127.0.0.1:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# 2단계: nginx로 화면 파일을 내려줍니다.
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
