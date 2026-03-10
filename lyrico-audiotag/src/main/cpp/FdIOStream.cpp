//
// Created by 32134 on 2026/3/10.
//

#include "FdIOStream.h"

#include <unistd.h>
#include <fcntl.h>
#include <stdexcept>

FdIOStream::FdIOStream(int fd)
        : m_fd(dup(fd)), m_position(0), m_size(0) {

    if (m_fd == -1)
        throw std::runtime_error("dup failed");

    struct stat st{};
    if (fstat(m_fd, &st) == 0)
        m_size = st.st_size;
}

FdIOStream::~FdIOStream() {
    if (m_fd != -1)
        close(m_fd);
}

TagLib::FileName FdIOStream::name() const {
    return "fd_stream";
}

TagLib::ByteVector FdIOStream::readBlock(size_t length) {
    TagLib::ByteVector data((unsigned int)length);

    ssize_t bytes = pread(m_fd, data.data(), length, m_position);
    if (bytes <= 0)
        return {};

    m_position += bytes;
    data.resize(bytes);
    return data;
}

void FdIOStream::writeBlock(const TagLib::ByteVector &data) {
    ssize_t written = pwrite(m_fd, data.data(), data.size(), m_position);

    if (written > 0)
        m_position += written;

    if (m_position > m_size)
        m_size = m_position;
}

void FdIOStream::insert(const TagLib::ByteVector &data, TagLib::offset_t start, size_t replace) {

    TagLib::offset_t tailStart = start + replace;
    TagLib::offset_t tailSize = m_size - tailStart;
    TagLib::offset_t dataSize = data.size();

    if (dataSize > replace) {

        TagLib::offset_t shiftAmount = dataSize - replace;
        TagLib::offset_t readPos = tailStart + tailSize;

        std::vector<char> buffer(IO_BUFFER_SIZE);

        TagLib::offset_t bytesToMove = tailSize;

        while (bytesToMove > 0) {

            size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);

            readPos -= chunk;

            pread(m_fd, buffer.data(), chunk, readPos);
            pwrite(m_fd, buffer.data(), chunk, readPos + shiftAmount);

            bytesToMove -= chunk;
        }

    } else if (dataSize < replace) {

        TagLib::offset_t shiftAmount = replace - dataSize;
        TagLib::offset_t readPos = tailStart;

        std::vector<char> buffer(IO_BUFFER_SIZE);

        TagLib::offset_t bytesToMove = tailSize;

        while (bytesToMove > 0) {

            size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);

            pread(m_fd, buffer.data(), chunk, readPos);
            pwrite(m_fd, buffer.data(), chunk, readPos - shiftAmount);

            readPos += chunk;
            bytesToMove -= chunk;
        }
    }

    if (dataSize > 0)
        pwrite(m_fd, data.data(), dataSize, start);

    m_size = start + dataSize + tailSize;

    ftruncate(m_fd, m_size);
}

void FdIOStream::removeBlock(TagLib::offset_t start, size_t length) {

    TagLib::offset_t tailStart = start + length;
    TagLib::offset_t tailSize = m_size - tailStart;

    std::vector<char> buffer(IO_BUFFER_SIZE);

    TagLib::offset_t readPos = tailStart;
    TagLib::offset_t writePos = start;

    TagLib::offset_t bytesToMove = tailSize;

    while (bytesToMove > 0) {

        size_t chunk = std::min((size_t)bytesToMove, IO_BUFFER_SIZE);

        pread(m_fd, buffer.data(), chunk, readPos);
        pwrite(m_fd, buffer.data(), chunk, writePos);

        readPos += chunk;
        writePos += chunk;

        bytesToMove -= chunk;
    }

    m_size -= length;

    ftruncate(m_fd, m_size);
}

bool FdIOStream::readOnly() const {
    return false;
}

bool FdIOStream::isOpen() const {
    return m_fd != -1;
}

void FdIOStream::seek(TagLib::offset_t offset, TagLib::IOStream::Position p) {

    switch (p) {
        case Beginning:
            m_position = offset;
            break;

        case Current:
            m_position += offset;
            break;

        case End:
            m_position = m_size + offset;
            break;
    }

    if (m_position < 0)
        m_position = 0;

    if (m_position > m_size)
        m_position = m_size;
}

TagLib::offset_t FdIOStream::tell() const {
    return m_position;
}

TagLib::offset_t FdIOStream::length() {
    return m_size;
}

void FdIOStream::truncate(TagLib::offset_t length) {

    ftruncate(m_fd, length);

    m_size = length;

    if (m_position > m_size)
        m_position = m_size;
}
