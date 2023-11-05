# Use Azurite image as a parent image
FROM mcr.microsoft.com/azure-storage/azurite

# Set the working directory
WORKDIR /usr/app

# Install necessary libraries
RUN npm install @azure/storage-blob https ssl-root-cas

# Copy all required files
COPY ./create-containers.js ./start.sh ./wait-for.sh ./127.0.0.1.pem ./127.0.0.1-key.pem ./

# Make sure the shell scripts are executable
RUN chmod +x start.sh wait-for.sh

# Make port 10000 available to the world outside this container
EXPOSE 10000

# Run start.sh when the container launches
CMD ["./start.sh"]
