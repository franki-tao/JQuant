# sabrabsprob.u32.gz

Absorption probabilities in the SABR model, converted from QuantLib
`ql/experimental/volatility/noarbsabrabsprobs.cpp`.

The numbers are taken from
http://cid-45980dfcc6f0d6c9.skydrive.live.com/self.aspx/.Public/AbsProbs.csv
For the paper reference see `noarbsabr.hpp` (Paul Doust, No-arbitrage SABR,
The Journal of Computational Finance, Volume 15/Number 3, Spring 2012).

Copyright (C) 2014 Peter Caspers

This file is part of QuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://quantlib.org/

QuantLib is free software: you can redistribute it and/or modify it
under the terms of the QuantLib license. You should have received a
copy of the license along with this program; if not, please email
<quantlib-dev@lists.sf.net>. The license is also available online at
<https://www.quantlib.org/license.shtml>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the license for more details.

## Format

- Gzip-compressed little-endian `uint32` array
- Length: `1,209,600` (official `extern "C" const unsigned long sabrabsprob[1209600]`)
- At use time each entry is divided by official `nsim = 2500000.0`
- Checksum: sum of the unsigned integer values = `647857029164`
- SHA-256 of the uncompressed little-endian bytes:
  `e229402f47e990416e60459b0f7a33748d8bbb80a1312bbca8b2d874a7ad7f9c`
- Uncompressed size: `4,838,400` bytes
- Gzip size: `3,613,810` bytes

Regenerate from the official C++ source with:

```
python3 JQuanlib/scripts/convert_sabrabsprob.py
```

The loader must verify the length and the sum checksum against the official
C++ array before the table is used.
