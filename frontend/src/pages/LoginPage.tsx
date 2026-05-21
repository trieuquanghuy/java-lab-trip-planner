import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/features/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';

const loginSchema = z.object({
  email: z.string().email('Invalid email format.'),
  password: z.string().min(8, 'Password must be at least 8 characters.'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, addToTripContext, setAddToTripContext } = useAuth();

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = async (values: LoginFormValues) => {
    try {
      await login(values);
      const ctx = addToTripContext;
      if (ctx) {
        setAddToTripContext(null);
        navigate(`/destinations/${ctx.destinationRef}`);
      } else {
        const next = searchParams.get('next');
        if (next && next.startsWith('/')) {
          navigate(next);
        } else {
          navigate('/');
        }
      }
    } catch (err: unknown) {
      const apiErr = (err as { response?: { data?: { code?: string; detail?: string } } })?.response?.data;
      if (apiErr?.code === 'auth.email_not_verified') {
        toast.error(apiErr.detail || 'Please verify your email first.');
      } else if (apiErr?.code === 'auth.invalid_credentials') {
        toast.error(apiErr.detail || 'Email or password is incorrect.');
      } else if (apiErr?.code === 'auth.rate_limited') {
        toast.error(apiErr.detail || 'Too many attempts. Please try again later.');
      } else {
        toast.error('Something went wrong. Please try again.');
      }
    }
  };

  return (
    <div className="flex justify-center mt-12 animate-slide-up">
      <Card className="w-full max-w-md transition-shadow hover:shadow-lg">
        <CardHeader>
          <CardTitle className="text-2xl">Log In</CardTitle>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" placeholder="you@example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" placeholder="••••••••" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Logging in...' : 'Log In'}
              </Button>
            </form>
          </Form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Don&apos;t have an account?{' '}
            <Link to="/signup" className="underline text-foreground">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
