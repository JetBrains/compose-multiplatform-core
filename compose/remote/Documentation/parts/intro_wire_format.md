!!! ERROR
    This is work in progress and subject to modifications.

Origami is a standalone content format for Android. It consists
of a list of operations that control the rendering.

Wire Format overview
======   

The Origami wire format is a simple flat list of Operations,
serialized in a binary format (see next sections for a detailed description of each operation).

!!! WARNING
    The origami wire format is intended to be used for fast generation and transport. For displaying purposes,
    a more runtime-appropriate document should be created and used instead.

Each list of operations contains as first element a Header (see section [Header]),
and the last operation will be a [End] element (see section [End]).

            |Operations|
            |:----:|
            | **Header** |
            | *Operation 1* |
            | *Operation 2* |
            | *Operation 3* |
            | ... |
            | *Operation n* |
            | **End** |

Encoding
--------

Operations are encoded in binary, using the following types:

    | Type | Size (in bytes) |
    |:----:|:----:|
    | BYTE | 1 |
    | BOOLEAN | 1 |
    | SHORT | 2 |
    | INT | 4 |
    | FLOAT | 4 |
    | LONG | 8 |
    | DOUBLE | 8 |
    | BUFFER | 4 + Size |
    | UTF8 | BUFFER |

A Buffer is simply encoded as the size of the buffer in bytes (encoded as an INT, so 4 bytes)
followed by the byte buffer itself. UTF8 payload is simply encoded as a byte Buffer using encodeToByteArray().



Components & Layout
-------------------

Operations can further be grouped in components, surrounded by ComponentStart (section [ComponentStart])
and ComponentEnd (section [ComponentEnd]).

            |Operations|
            |:----:|
            | ... |
            | **ComponentStart** |
            | *Operation 1* |
            | *Operation 2* |
            | *Operation 3* |
            | ... |
            | *Operation n* |
            | **ComponentEnd** |
            | ... |

