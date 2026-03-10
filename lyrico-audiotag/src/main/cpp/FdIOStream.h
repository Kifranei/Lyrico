//
// Created by 32134 on 2026/3/10.
//

#ifndef LYRICO_FDIOSTREAM_H
#define LYRICO_FDIOSTREAM_H

#include <taglib/tiostream.h>
#include <taglib/tbytevector.h>

#include <vector>
#include <unistd.h>
#include <sys/stat.h>

class FdIOStream : public TagLib::IOStream {
public:
    explicit FdIOStream(int fd);
    ~FdIOStream() override;

    [[nodiscard]] TagLib::FileName name() const override;

    TagLib::ByteVector readBlock(size_t length) override;
    void writeBlock(const TagLib::ByteVector &data) override;

    void insert(const TagLib::ByteVector &data, TagLib::offset_t start, size_t replace) override;
    void removeBlock(TagLib::offset_t start, size_t length) override;

    [[nodiscard]] bool readOnly() const override;
    [[nodiscard]] bool isOpen() const override;

    void seek(TagLib::offset_t offset, TagLib::IOStream::Position p) override;

    [[nodiscard]] TagLib::offset_t tell() const override;
    TagLib::offset_t length() override;
    void truncate(TagLib::offset_t length) override;

private:
    static constexpr size_t IO_BUFFER_SIZE = 8192;

    int m_fd;
    TagLib::offset_t m_position;
    TagLib::offset_t m_size;
};


#endif //LYRICO_FDIOSTREAM_H
