FROM node:20-alpine

WORKDIR /app

# Install production dependencies first for better layer caching.
COPY package.json ./
RUN npm install --omit=dev

# Copy application source.
COPY . .

EXPOSE 4000

CMD ["node", "server.js"]
