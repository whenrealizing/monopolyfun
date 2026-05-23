"use client";

import { useState } from "react";
import { Check, Copy, Download, FileText } from "lucide-react";

import { Button } from "@/components/ui/button";
import { createUploadDownload } from "@/lib/api";
import { presentError } from "@/lib/error-messages";

type EvidenceListLabels = {
  empty?: string;
  download?: string;
  open?: string;
  copy?: string;
  copied?: string;
  uploadPrefix?: string;
};

export function DisputeEvidenceList({ evidenceRefs, labels = {} }: { evidenceRefs: string[]; labels?: EvidenceListLabels }) {
  const [error, setError] = useState<string | null>(null);
  const [loadingRef, setLoadingRef] = useState<string | null>(null);
  const [copiedRef, setCopiedRef] = useState<string | null>(null);

  if (evidenceRefs.length === 0) {
    return (
      <div className="rounded-[8px] border border-dashed border-[var(--border)] bg-[var(--surface-1)] p-4 text-sm font-semibold text-[var(--muted-foreground)]">
        {labels.empty ?? "等待补充证据。"}
      </div>
    );
  }

  async function downloadArtifact(ref: string) {
    const assetId = ref.replace(/^asset:\/\//, "");
    setError(null);
    setLoadingRef(ref);
    try {
      // 中文注释：asset 证据先换取短期下载地址，避免把私有对象地址直接暴露在页面上。
      const download = await createUploadDownload(assetId);
      window.open(download.downloadUrl, "_blank", "noopener,noreferrer");
    } catch (caught) {
      setError(presentError(caught, "ui.upload.failed").message);
    } finally {
      setLoadingRef(null);
    }
  }

  async function copyEvidenceRef(ref: string) {
    setError(null);
    try {
      await navigator.clipboard.writeText(ref);
      setCopiedRef(ref);
      window.setTimeout(() => setCopiedRef((current) => current === ref ? null : current), 1400);
    } catch (caught) {
      setError(presentError(caught, "common.action.failed").message);
    }
  }

  return (
    <div className="space-y-2">
      {error ? (
        <div className="rounded-[8px] border border-[rgba(245,98,98,0.28)] bg-[rgba(245,98,98,0.1)] p-3 text-xs font-semibold text-[var(--foreground)]">
          {error}
        </div>
      ) : null}
      {evidenceRefs.map((ref) => (
        <div key={ref} className="flex flex-wrap items-center justify-between gap-3 py-2">
          <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-[var(--foreground)]">
            <FileText className="h-4 w-4 shrink-0 text-[var(--accent-blue)]" />
            {isHttpUrl(ref) ? (
              <a href={ref} target="_blank" rel="noreferrer" className="break-all underline-offset-4 hover:underline">
                {evidenceLabel(ref, labels)}
              </a>
            ) : (
              <span className="break-all">{evidenceLabel(ref, labels)}</span>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <Button
              type="button"
              size="icon"
              variant="ghost"
              className="h-8 w-8 rounded-[8px]"
              aria-label={copiedRef === ref ? labels.copied ?? "已复制" : labels.copy ?? "复制链接"}
              title={copiedRef === ref ? labels.copied ?? "已复制" : labels.copy ?? "复制链接"}
              onClick={() => void copyEvidenceRef(ref)}
            >
              {copiedRef === ref ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
            </Button>
            {isAssetRef(ref) ? (
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="h-8 w-8 rounded-[8px]"
                loading={loadingRef === ref}
                aria-label={labels.download ?? "下载文件"}
                title={labels.download ?? "下载文件"}
                onClick={() => void downloadArtifact(ref)}
              >
                <Download className="h-3.5 w-3.5" />
              </Button>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  );
}

function isAssetRef(ref: string) {
  return ref.startsWith("asset://");
}

function isHttpUrl(ref: string) {
  return /^https?:\/\//i.test(ref);
}

function evidenceLabel(ref: string, labels: EvidenceListLabels) {
  if (isAssetRef(ref)) return `${labels.uploadPrefix ?? "上传文件"} ${ref.replace(/^asset:\/\//, "")}`;
  return ref;
}
