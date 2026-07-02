import { useState } from 'react';
import { Share2, Link, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { useGenerateShare, useRevokeShare } from './trip.hooks';

interface ShareButtonProps {
  tripId: string;
  shareToken: string | null;
  shareEnabled: boolean;
}

export function ShareButton({ tripId, shareToken, shareEnabled }: ShareButtonProps) {
  const [open, setOpen] = useState(false);
  const generateShare = useGenerateShare(tripId);
  const revokeShare = useRevokeShare(tripId);

  const shareUrl = shareToken
    ? `${window.location.origin}/share/${shareToken}`
    : null;

  const handleShare = () => {
    if (!shareEnabled) {
      generateShare.mutate(undefined, {
        onSuccess: () => setOpen(true),
      });
    } else {
      setOpen(true);
    }
  };

  const handleCopy = () => {
    if (shareUrl) {
      navigator.clipboard.writeText(shareUrl);
      toast.success('Link copied!');
    }
  };

  const handleRevoke = () => {
    revokeShare.mutate(undefined, {
      onSuccess: () => setOpen(false),
    });
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          onClick={handleShare}
          disabled={generateShare.isPending}
          className="p-2 h-11 w-11 sm:h-9 sm:w-9 flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-50"
          aria-label="Share trip"
        >
          <Share2 className="w-5 h-5" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-3" align="end">
        <p className="text-sm font-semibold mb-2">Share this trip</p>
        {shareUrl && (
          <div className="flex items-center gap-2 mb-3">
            <button
              onClick={handleCopy}
              className="flex items-center gap-2 flex-1 text-left text-xs bg-muted rounded-md px-2 py-1.5 hover:bg-muted/80 transition-colors truncate"
              title={shareUrl}
            >
              <Link className="w-3 h-3 shrink-0" />
              <span className="truncate">{shareUrl}</span>
            </button>
          </div>
        )}
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">Anyone with the link can view</p>
          {shareEnabled && (
            <button
              onClick={handleRevoke}
              disabled={revokeShare.isPending}
              className="flex items-center gap-1 text-xs text-destructive hover:text-destructive/80 transition-colors disabled:opacity-50"
            >
              <Trash2 className="w-3 h-3" />
              Revoke
            </button>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
