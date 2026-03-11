#!/usr/bin/env python3
import random

# ====== Configuration ======
# Invalid ranges per denomination (from the problem)
INVALID_RANGES = {
    10: [(67250001, 67700000), (76310012, 85139995)],
    20: [(87280145, 91646549), (118700001, 119600000)],
    50: [(77100001, 77550000), (108050001, 108500000)],
}

# Desired counts per denomination and validity
COUNTS = {
    10: {'valid': 9, 'invalid': 9},
    20: {'valid': 8, 'invalid': 8},
    50: {'valid': 8, 'invalid': 8},
}

# Scale factor for two bills per row on A4
SCALE = 0.65

# ====== Helper functions ======
def is_invalid(denom, serial):
    """Return True if serial is in any invalid range for the denomination."""
    for lo, hi in INVALID_RANGES[denom]:
        if lo <= serial <= hi:
            return True
    return False

def generate_valid_serial(denom):
    """Generate a random 9-digit serial not in any invalid range."""
    while True:
        serial = random.randint(1, 999999999)  # 1 to 9 digits (we'll pad)
        if not is_invalid(denom, serial):
            return serial

def generate_invalid_serial(denom):
    """Generate a random serial from one of the invalid ranges."""
    ranges = INVALID_RANGES[denom]
    lo, hi = random.choice(ranges)
    return random.randint(lo, hi)

def format_serial(serial):
    """Return serial as a zero-padded 9-digit string."""
    return f"{serial:09d}"

# ====== Generate all bills ======
bills = []

# Valid bills (Series A)
for denom, counts in COUNTS.items():
    for _ in range(counts['valid']):
        serial = generate_valid_serial(denom)
        bills.append((denom, format_serial(serial), 'A', 'valid'))

# Invalid bills (Series B)
for denom, counts in COUNTS.items():
    for _ in range(counts['invalid']):
        serial = generate_invalid_serial(denom)
        bills.append((denom, format_serial(serial), 'B', 'invalid'))

# Shuffle the list to mix valid and invalid
random.shuffle(bills)

# ====== Generate LaTeX code ======
latex_preamble = r"""\documentclass[a4paper]{article}
\usepackage{tikz}
\usepackage{longtable}
\usepackage{geometry}
\geometry{margin=1cm}

% Command to draw a single bill
% #1 = denomination, #2 = serial (9 digits), #3 = series, #4 = validity (valid/invalid)
\newcommand{\drawbill}[4]{%
  \begin{tikzpicture}
    \draw (0,0) rectangle (14,7);
    % top left small denomination
    \node[anchor=north west] at (1,6.5) {\small #1};
    % bottom left small denomination
    \node[anchor=south west] at (1,1.0) {\small #1};
    % bottom left serial + series
    \node[anchor=south west] at (1,0.5) {#2 #3};
    % top right serial + series
    \node[anchor=north east] at (13,6.5) {#2 #3};
    % bottom right large denomination
    \node[anchor=south east] at (13,0.5) {\Huge #1};
    % validity label (tiny, gray) – comment out if not wanted
    \ifnum#4=1
      \node[anchor=south, gray!50] at (7,0.2) {\tiny VALID};
    \else
      \node[anchor=south, gray!50] at (7,0.2) {\tiny INVALID};
    \fi
  \end{tikzpicture}%
}

\begin{document}

\begin{center}
\begin{longtable}{cc}
\hline
\endhead
"""

latex_footer = r"""
\end{longtable}
\end{center}

\end{document}
"""

# Build the table rows
rows = []
for i, (denom, serial, series, validity) in enumerate(bills):
    # validity as 1 for valid, 0 for invalid (for the \ifnum in drawbill)
    val_code = 1 if validity == 'valid' else 0
    bill_code = f"\\scalebox{{{SCALE}}}{{\\drawbill{{{denom}}}{{{serial}}}{{{series}}}{{{val_code}}}}}"
    if i % 2 == 0:
        # First column
        rows.append(bill_code + " &")
    else:
        # Second column with line break
        rows.append(bill_code + " \\\\")

# Join rows with newlines
table_body = "\n".join(rows)

# Output full LaTeX document
print(latex_preamble)
print(table_body)
print(latex_footer)