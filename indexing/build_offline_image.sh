#!/bin/bash

# Build script for offline deployment image
# This script creates a Docker image with all dependencies included

set -e

echo "🚀 Building offline deployment image..."

# Define image name and tag
IMAGE_NAME="indexing-server-offline"
IMAGE_TAG="latest"
FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"

# Create a directory for offline dependencies if it doesn't exist
mkdir -p ./offline-deps

# Step 1: Build the offline image for AMD64 architecture
echo "📦 Building Docker image with all dependencies for AMD64..."
docker build --platform linux/amd64 -f Dockerfile.offline -t "${FULL_IMAGE_NAME}" .

# Step 2: Create a tarball of the image for transfer
echo "📁 Creating image tarball for offline deployment..."
docker save -o "${IMAGE_NAME}-amd64.tar" "${FULL_IMAGE_NAME}"

# Step 3: Compress the tarball
echo "🗜️ Compressing image tarball..."
gzip "${IMAGE_NAME}-amd64.tar"

# Step 4: Show final file size
FINAL_FILE="${IMAGE_NAME}-amd64.tar.gz"
if [ -f "$FINAL_FILE" ]; then
    FILE_SIZE=$(du -h "$FINAL_FILE" | cut -f1)
    echo "✅ Offline deployment image created successfully!"
    echo "📏 Final compressed size: $FILE_SIZE"
    echo "📄 File location: ./$FINAL_FILE"
    echo ""
    echo "🚢 To deploy on offline environment:"
    echo "1. Transfer $FINAL_FILE to target server"
    echo "2. Run: gunzip $FINAL_FILE"
    echo "3. Run: docker load -i ${IMAGE_NAME}-amd64.tar"
    echo "4. Run: docker run -p 8000:8000 $FULL_IMAGE_NAME"
else
    echo "❌ Failed to create compressed image file"
    exit 1
fi

echo ""
echo "🔍 Image details:"
docker images | grep "$IMAGE_NAME"
