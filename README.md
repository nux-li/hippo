# hippo
Hugo Image Preprocessor (Hippo) is a command line tool written in Kotlin, developed to be used together with Hugo,  one of the most popular open-source static site generators.

Hippo extracts metadata from JPEGs, creates a set of resized images with or without watermarks. The metadata is placed in the front matter, one markdown file for each image. For every folder with images, an album markdown file is also created.

The Hugo template [Foto](https://github.com/nux-li/hugo-foto-theme) uses Hippo, and has step by step instructions on how to create a photo gallery site using Hugo.

```text
Usage: hippo [<options>] [<directory>]

Options:
  -c, --changes=(front_matter|image_metadata|both)
                            From which source should changes be accepted?
  -d, --demo                If specified demo photos will be used
  -f, --format=(json|yaml)  The format to be used for front matter segment
  -p, --precedence=(front_matter|image_metadata)
                            If both the Hugo front matter and the image metadata have changed, which one takes
                            precedence?
  -w, --watermark=(UL|UR|LL|LR|UL_C|UR_C|LL_C|LR_C)
                            Specify this to add a watermark to the photos. Allowed variants:
                            
                            UL --> Watermark in upper left corner
                            
                            UR --> Watermark in upper right corner
                            
                            LL --> Watermark in lower left corner
                            
                            LR --> Watermark in lower left corner
                            
                            UL_C --> Watermark in upper left corner, with additional subtle center watermark
                            
                            UR_C --> Watermark in upper right corner, with additional subtle center watermark
                            
                            LL_C --> Watermark in lower left corner, with additional subtle center watermark
                            
                            LR_C --> Watermark in lower left corner, with additional subtle center watermark
  --verbose                 Log detailed information
  --clear                   Remove demo files if existing. Also removed the database. Use with caution!
  --regenerate              Remove markdown files if existing. Also removed the database. Use with caution!
  --refine                  Refine front matter in existing markdown files.
  -h, --help                Show this message and exit

Arguments:
  <directory>  Path to the content directory for your Hugo website project

```