#!/bin/bash

# Docker build and export script for docs application
# This script builds the Docker image for amd64 architecture and exports it as a tar file

set -e  # Exit on any error

# Configuration
IMAGE_NAME="docs-app"
IMAGE_TAG="latest"
EXPORT_FILE="docs-app-image.tar"
BUILD_PLATFORM="linux/amd64"

echo "🚀 Starting Docker build process..."
echo "📋 Configuration:"
echo "   - Image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo "   - Platform: ${BUILD_PLATFORM}"
echo "   - Export file: ${EXPORT_FILE}"
echo ""

# Clean up any existing image with the same name
echo "🧹 Cleaning up existing images..."
docker rmi ${IMAGE_NAME}:${IMAGE_TAG} 2>/dev/null || true

# Build the Docker image for amd64 architecture
echo "🔨 Building Docker image for amd64 architecture..."
docker build \
  --platform ${BUILD_PLATFORM} \
  --tag ${IMAGE_NAME}:${IMAGE_TAG} \
  --no-cache \
  .

# Verify the image was built successfully
echo "✅ Verifying image build..."
docker images ${IMAGE_NAME}:${IMAGE_TAG}

# Export the image to a tar file
echo "📦 Exporting image to tar file..."
docker save ${IMAGE_NAME}:${IMAGE_TAG} -o ${EXPORT_FILE}

# Compress the tar file with gzip
echo "🗜️  Compressing tar file..."
gzip -f ${EXPORT_FILE}
COMPRESSED_FILE="${EXPORT_FILE}.gz"

# Display file information
echo ""
echo "✅ Build completed successfully!"
echo "📊 File information:"
ls -lh ${COMPRESSED_FILE}

echo ""
echo "🎉 Docker image has been built and compressed!"
echo "📁 Compressed file: ${COMPRESSED_FILE}"
echo ""
echo "📤 To transfer to server, use:"
echo "   scp ${COMPRESSED_FILE} user@server:/path/to/destination/"
echo ""
echo "📥 To load on server, use:"
echo "   gunzip ${COMPRESSED_FILE}"
echo "   docker load -i ${EXPORT_FILE}"
echo "   docker run -d -p 8080:8080 --name docs-container ${IMAGE_NAME}:${IMAGE_TAG}"
