#include <CoreFoundation/CoreFoundation.h>
#include <CoreGraphics/CoreGraphics.h>
#include <ImageIO/ImageIO.h>
#include <stdio.h>
#include <stdlib.h>

static CFURLRef file_url(const char *path) {
    CFStringRef string = CFStringCreateWithCString(
        kCFAllocatorDefault,
        path,
        kCFStringEncodingUTF8
    );
    CFURLRef url = CFURLCreateWithFileSystemPath(
        kCFAllocatorDefault,
        string,
        kCFURLPOSIXPathStyle,
        false
    );
    CFRelease(string);
    return url;
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fputs("usage: remove_generated_checkerboard input.png output.png\n", stderr);
        return 2;
    }
    CFURLRef input = file_url(argv[1]);
    CGImageSourceRef source = CGImageSourceCreateWithURL(input, NULL);
    CGImageRef image = source ? CGImageSourceCreateImageAtIndex(source, 0, NULL) : NULL;
    if (!image) {
        fputs("could not decode input PNG\n", stderr);
        return 3;
    }

    const size_t width = CGImageGetWidth(image);
    const size_t height = CGImageGetHeight(image);
    const size_t bytes_per_row = width * 4;
    unsigned char *pixels = calloc(height, bytes_per_row);
    CGColorSpaceRef color_space = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(
        pixels,
        width,
        height,
        8,
        bytes_per_row,
        color_space,
        kCGImageAlphaPremultipliedLast
    );
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);

    for (size_t offset = 0; offset < height * bytes_per_row; offset += 4) {
        const int r = pixels[offset];
        const int g = pixels[offset + 1];
        const int b = pixels[offset + 2];
        const int max = r > g ? (r > b ? r : b) : (g > b ? g : b);
        const int min = r < g ? (r < b ? r : b) : (g < b ? g : b);
        const int luminance = (r + g + b) / 3;
        // Image generation returned a neutral gray transparency preview. Remove
        // only its low-chroma light squares; warm gold and red paper stay opaque.
        if (max - min <= 12 && luminance >= 205) {
            pixels[offset] = 0;
            pixels[offset + 1] = 0;
            pixels[offset + 2] = 0;
            pixels[offset + 3] = 0;
        }
    }

    CGImageRef result = CGBitmapContextCreateImage(context);
    CFURLRef output = file_url(argv[2]);
    CGImageDestinationRef destination = CGImageDestinationCreateWithURL(
        output,
        CFSTR("public.png"),
        1,
        NULL
    );
    if (!destination) {
        fputs("could not create output PNG\n", stderr);
        return 4;
    }
    CGImageDestinationAddImage(destination, result, NULL);
    const bool success = CGImageDestinationFinalize(destination);

    CFRelease(destination);
    CFRelease(output);
    CGImageRelease(result);
    CGContextRelease(context);
    CGColorSpaceRelease(color_space);
    free(pixels);
    CGImageRelease(image);
    CFRelease(source);
    CFRelease(input);
    return success ? 0 : 5;
}
