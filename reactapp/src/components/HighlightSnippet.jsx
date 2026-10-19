import React from "react";

/**
 * Safely highlights occurrences of `query` within `snippet` without using dangerouslySetInnerHTML.
 * Splits by case-insensitive regex pattern and wraps matched portions in <mark> tags.
 */
export default function HighlightSnippet({ snippet, query, className = "" }) {
  if (!snippet) return null;
  if (!query || !query.trim()) {
    return <span className={className}>{snippet}</span>;
  }

  const trimmedQuery = query.trim();
  // Escape regex special characters
  const escapedQuery = trimmedQuery.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regex = new RegExp(`(${escapedQuery})`, "gi");
  const parts = snippet.split(regex);

  return (
    <span className={`highlight-snippet-container ${className}`}>
      {parts.map((part, i) => {
        const isMatch = part.toLowerCase() === trimmedQuery.toLowerCase();
        return isMatch ? (
          <mark key={i} className="search-match-highlight">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        );
      })}
    </span>
  );
}
