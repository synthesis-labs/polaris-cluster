FROM node:8.11-stretch
WORKDIR app
COPY package.json .
RUN npm install
COPY public ./public
COPY src ./src
CMD ["npm", "start"]
