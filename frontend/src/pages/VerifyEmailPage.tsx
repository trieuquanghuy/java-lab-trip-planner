import { useEffect } from 'react';
import { useSearchParams, Link, Navigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const status = searchParams.get('status');

  useEffect(() => {
    if (status === 'success') {
      toast.success('Email verified — please log in');
    }
  }, [status]);

  if (!status || !['check-email', 'success', 'invalid', 'expired'].includes(status)) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="flex justify-center mt-12">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl">
            {status === 'check-email' && 'Check your email'}
            {status === 'success' && 'Email verified!'}
            {status === 'invalid' && 'Invalid verification link'}
            {status === 'expired' && 'Verification link expired'}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {status === 'check-email' && (
            <p className="text-muted-foreground">
              We sent a verification link to your email. Click it to activate your account.
            </p>
          )}
          {status === 'success' && (
            <>
              <p className="text-muted-foreground">You can now log in.</p>
              <Button asChild className="w-full">
                <Link to="/login">Go to Login</Link>
              </Button>
            </>
          )}
          {status === 'invalid' && (
            <p className="text-muted-foreground">
              This link is invalid or has already been used.
            </p>
          )}
          {status === 'expired' && (
            <p className="text-muted-foreground">
              This link has expired. Please sign up again.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
